package dev.padelpot.persistence;

import dev.padelpot.domain.PlayObservation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.pipelineframework.mapper.Mapper;

@ApplicationScoped
public class PlayObservationMapper implements Mapper<PlayObservation, PlayObservationEntity> {
  @Inject OwnerMapper owners;
  @Inject MatchMapper matches;

  public PlayObservationEntity toExternal(PlayObservation observation) {
    var entity = new PlayObservationEntity();
    entity.revisionId = observation.match().id() + ":" + observation.match().version();
    entity.owners.addAll(observation.owners().stream().map(owners::toExternal).toList());
    entity.match = matches.toExternal(observation.match());
    return entity;
  }

  public PlayObservation fromExternal(PlayObservationEntity entity) {
    return new PlayObservation(
        entity.owners.stream().map(owners::fromExternal).toList(),
        matches.fromExternal(entity.match));
  }
}
