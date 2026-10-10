package dev.padelpot.persistence;

import dev.padelpot.domain.ChangeReceipt;
import jakarta.persistence.*;

/** The authorised business change that produced this aggregate version. */
@Embeddable
public class ChangeReceiptEntity {
  @Column(name = "change_request")
  public String requestId;

  @Column(name = "change_owner")
  public String ownerId;

  @Column(name = "change_status")
  public String status;

  @Column(name = "change_message", length = 2400)
  public String message;

  public static ChangeReceiptEntity fromDomain(ChangeReceipt receipt) {
    var entity = new ChangeReceiptEntity();
    entity.requestId = receipt.requestId();
    entity.ownerId = receipt.ownerId();
    entity.status = receipt.status();
    entity.message = receipt.message();
    return entity;
  }

  public ChangeReceipt toDomain() {
    return new ChangeReceipt(requestId, ownerId, status, message);
  }
}
