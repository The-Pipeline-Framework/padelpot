package dev.padelpot.persistence;

import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "match_revision")
public class MatchEntity {
  @Id public String revisionId;
  @Embedded public ChangeReceiptEntity change;
  public String matchId, organizer, style, area, matchDate, level, status;

  @Column(name = "match_hour")
  public int hour;

  public long matchVersion;
  public boolean cancelled, played;

  @ElementCollection(fetch = FetchType.EAGER)
  public Map<String, String> players = new LinkedHashMap<>();
}
