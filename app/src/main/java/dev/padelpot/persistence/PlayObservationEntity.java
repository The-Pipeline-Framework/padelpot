package dev.padelpot.persistence;

import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "play_observation")
public class PlayObservationEntity {
  @Id public String revisionId;

  @OneToMany(cascade = CascadeType.PERSIST)
  public List<OwnerEntity> owners = new ArrayList<>();

  @OneToOne(cascade = CascadeType.PERSIST)
  public MatchEntity match;
}
