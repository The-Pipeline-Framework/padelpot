package dev.padelpot.persistence;

import dev.padelpot.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.*;

/**
 * Local database observation and explicit erasure for the demo; writes belong to the TPF aspect.
 */
@ApplicationScoped
public class AggregateQueries {
  @Inject EntityManager entities;
  @Inject OwnerMapper owners;
  @Inject MatchMapper matches;

  public record HistoryTurn(long cursor, String eventId, String speaker, String text, String occurredAt) {}

  public record HistoryPage(List<HistoryTurn> turns, Optional<Long> nextCursor) {
    public HistoryPage {
      turns = List.copyOf(turns);
      nextCursor = Objects.requireNonNull(nextCursor);
    }
  }

  @Transactional
  public HistoryPage historyPage(String tenantId, String ownerId, long afterCursor, int limit) {
    if (afterCursor < 0 || limit < 1 || limit > 100)
      throw new IllegalArgumentException("Invalid history cursor or limit");
    var rows =
        entities
            .createQuery(
                "from ApprovedTurnEntity where tenantId = :tenant and ownerId = :owner and cursor > :after and (status is null or status <> 'HISTORY_ERASED') order by cursor",
                ApprovedTurnEntity.class)
            .setParameter("tenant", tenantId)
            .setParameter("owner", ownerId)
            .setParameter("after", afterCursor)
            .setMaxResults(limit + 1)
            .getResultList();
    var page = rows.stream().limit(limit).map(AggregateQueries::toHistoryTurn).toList();
    return new HistoryPage(
        page,
        rows.size() > limit ? Optional.of(page.getLast().cursor()) : Optional.empty());
  }

  private static HistoryTurn toHistoryTurn(ApprovedTurnEntity entity) {
    return new HistoryTurn(
        entity.cursor, entity.eventId, entity.speaker, entity.text, entity.occurredAt.toString());
  }

  /** A finite, owner-scoped observation. The enclosing persona.snapshot Query captures its result. */
  @Transactional
  public List<HistoryTurn> recentHistory(String tenantId, String ownerId, String currentRequestId, int limit) {
    if (limit < 1 || limit > 100) throw new IllegalArgumentException("History limit must be 1..100");
    var newest =
        entities
            .createQuery(
                "from ApprovedTurnEntity where tenantId = :tenant and ownerId = :owner and requestId <> :current and (status is null or status <> 'HISTORY_ERASED') order by cursor desc",
                ApprovedTurnEntity.class)
            .setParameter("tenant", tenantId)
            .setParameter("owner", ownerId)
            .setParameter("current", currentRequestId)
            .setMaxResults(limit)
            .getResultList();
    var chronological = new ArrayList<HistoryTurn>();
    for (int index = newest.size() - 1; index >= 0; index--) {
      var turn = newest.get(index);
      chronological.add(toHistoryTurn(turn));
    }
    return List.copyOf(chronological);
  }

  @Transactional
  public List<HistoryTurn> allHistory(String tenantId, String ownerId) {
    return entities
        .createQuery(
            "from ApprovedTurnEntity where tenantId = :tenant and ownerId = :owner and (status is null or status <> 'HISTORY_ERASED') order by cursor",
            ApprovedTurnEntity.class)
        .setParameter("tenant", tenantId)
        .setParameter("owner", ownerId)
        .getResultStream()
        .map(AggregateQueries::toHistoryTurn)
        .toList();
  }

  @Transactional
  public int eraseConversation(String tenantId, String ownerId) {
    var turns =
        entities
            .createQuery(
                "from ApprovedTurnEntity where tenantId = :tenant and ownerId = :owner",
                ApprovedTurnEntity.class)
            .setParameter("tenant", tenantId)
            .setParameter("owner", ownerId)
            .getResultList();
    turns.forEach(entities::remove);
    return turns.size();
  }

  @Transactional
  public List<Owner> owners() {
    var latest = new LinkedHashMap<String, Owner>();
    entities
        .createQuery("from OwnerEntity order by personaVersion", OwnerEntity.class)
        .getResultList()
        .forEach(e -> latest.put(e.ownerId, owners.fromExternal(e)));
    return List.copyOf(latest.values());
  }

  @Transactional
  public List<Match> matches() {
    var latest = new LinkedHashMap<String, Match>();
    entities
        .createQuery("from MatchEntity order by matchVersion", MatchEntity.class)
        .getResultList()
        .forEach(e -> latest.put(e.matchId, matches.fromExternal(e)));
    return List.copyOf(latest.values());
  }

  @Transactional
  public void eraseOwnerHistory(String ownerId) {
    for (var observation :
        entities
            .createQuery("from PlayObservationEntity", PlayObservationEntity.class)
            .getResultList()) observation.owners.removeIf(o -> o.ownerId.equals(ownerId));
    entities.flush();
    entities
        .createQuery("from OwnerEntity where ownerId = :owner", OwnerEntity.class)
        .setParameter("owner", ownerId)
        .getResultList()
        .forEach(entities::remove);
  }

  @Transactional
  public void clearFixtures() {
    entities
        .createQuery("from ApprovedTurnEntity", ApprovedTurnEntity.class)
        .getResultList()
        .forEach(entities::remove);
    entities
        .createQuery("from PlayObservationEntity", PlayObservationEntity.class)
        .getResultList()
        .forEach(entities::remove);
    entities.flush();
    entities
        .createQuery("from OwnerEntity", OwnerEntity.class)
        .getResultList()
        .forEach(entities::remove);
    entities
        .createQuery("from MatchEntity", MatchEntity.class)
        .getResultList()
        .forEach(entities::remove);
  }
}
