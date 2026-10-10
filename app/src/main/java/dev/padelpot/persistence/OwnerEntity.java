package dev.padelpot.persistence;

import jakarta.persistence.*;
import java.util.*;

/** A versioned persistence representation; never passed to a business step or an agent. */
@Entity
@Table(name = "owner_revision")
public class OwnerEntity {
  @Id public String revisionId;
  @Embedded public ChangeReceiptEntity change;
  public String ownerId;
  public long personaVersion;
  public String name, color;

  @ElementCollection(fetch = FetchType.EAGER)
  public Map<String, StoredFact> facts = new LinkedHashMap<>();

  @ElementCollection(fetch = FetchType.EAGER)
  public Set<String> blocked = new LinkedHashSet<>();

  @Embeddable
  public static class StoredFact {
    @Column(name = "fact_value")
    public String value;

    public String source, disclosure;

    public StoredFact() {}

    public StoredFact(String value, String source, String disclosure) {
      this.value = value;
      this.source = source;
      this.disclosure = disclosure;
    }
  }
}
