package dev.padelpot.persistence;

import dev.padelpot.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;
import org.pipelineframework.mapper.Mapper;

@ApplicationScoped
public class OwnerMapper implements Mapper<Owner, OwnerEntity> {
  public OwnerEntity toExternal(Owner owner) {
    var entity = new OwnerEntity();
    entity.change = ChangeReceiptEntity.fromDomain(owner.change());
    entity.revisionId = owner.id() + ":" + owner.version();
    entity.ownerId = owner.id();
    entity.personaVersion = owner.version();
    entity.name = owner.name();
    entity.color = owner.color();
    owner
        .facts()
        .forEach(
            f ->
                entity.facts.put(
                    f.field(), new OwnerEntity.StoredFact(f.value(), f.source(), f.disclosure())));
    entity.blocked.addAll(owner.blocked());
    return entity;
  }

  public Owner fromExternal(OwnerEntity entity) {
    return new Owner(
        entity.ownerId,
        entity.name,
        entity.color,
        entity.personaVersion,
        entity.facts.entrySet().stream()
            .map(
                e ->
                    new PersonaFact(
                        e.getKey(),
                        e.getValue().value,
                        e.getValue().source,
                        e.getValue().disclosure))
            .toList(),
        List.copyOf(entity.blocked),
        entity.change.toDomain());
  }
}
