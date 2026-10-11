package dev.padelpot.persistence;

import dev.padelpot.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.mapper.Mapper;

@ApplicationScoped
public class MatchMapper implements Mapper<Match, MatchEntity> {
  public MatchEntity toExternal(Match match) {
    var entity = new MatchEntity();
    entity.change = ChangeReceiptEntity.fromDomain(match.change());
    entity.revisionId = match.id() + ":" + match.version();
    entity.matchId = match.id();
    entity.matchVersion = match.version();
    entity.organizer = match.organizer();
    entity.style = match.style();
    entity.area = match.area();
    entity.matchDate = match.date();
    entity.level = match.level();
    entity.status = match.status();
    entity.hour = match.hour();
    entity.cancelled = match.cancelled();
    entity.played = match.played();
    match.players().forEach(p -> entity.players.put(p.ownerId(), p.side()));
    match.players().forEach(p -> entity.teams.put(p.ownerId(), p.team()));
    return entity;
  }

  public Match fromExternal(MatchEntity entity) {
    return new Match(
        entity.matchId,
        entity.organizer,
        entity.style,
        entity.area,
        entity.matchDate,
        entity.level,
        entity.status,
        entity.hour,
        entity.matchVersion,
        entity.players.entrySet().stream()
            .map(
                e ->
                    new Participation(
                        e.getKey(), e.getValue(), entity.teams.getOrDefault(e.getKey(), "")))
            .toList(),
        entity.cancelled,
        entity.played,
        entity.change.toDomain());
  }
}
