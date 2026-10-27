package dev.padelpot.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

/** Application-owned, immutable accepted dialogue value. Event IDs survive admission retries. */
@Entity
@Table(name = "approved_conversation_turn", indexes = @Index(columnList = "tenantId,ownerId,cursor"))
public class ApprovedTurnEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long cursor;
  @Column(nullable = false, unique = true) public String eventId;
  @Column(nullable = false) public String tenantId;
  @Column(nullable = false) public String ownerId;
  @Column(nullable = false) public String conversationId;
  @Column(nullable = false) public String requestId;
  @Column(nullable = false) public String speaker;
  @Column(nullable = false) public Instant occurredAt;
  @Column(nullable = false, length = 4096) public String text;
  @Column(length = 4096) public String ownerText;

  // The selected persistence mapping is reversible without storing model prompts or proposals.
  public String actorId, rootOwnerId, mode, matchId, style, side, lastAction, team, status;
  public String pendingInteractionId;
  public String reviewKind, reviewField, reviewValue;
  @Column(name = "turn_number") public int turn;
  @Column(name = "requested_hour") public int hour;
  public long deadline, ownerVersion, organizerVersion;
  @ElementCollection(fetch = FetchType.EAGER) public List<String> transcript = new ArrayList<>();
}
