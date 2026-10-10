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
