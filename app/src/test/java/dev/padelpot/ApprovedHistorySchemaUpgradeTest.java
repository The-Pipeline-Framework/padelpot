package dev.padelpot;

import dev.padelpot.persistence.ApprovedTurnEntity;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApprovedHistorySchemaUpgradeTest {
  @Test
  void addsReviewRoundWithoutDiscardingExistingHistory() throws Exception {
    String url = "jdbc:h2:mem:history-upgrade-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    long cursor;
    try (var factory = factory(url, "create"); var session = factory.openSession()) {
      var transaction = session.beginTransaction();
      var turn = new ApprovedTurnEntity();
      turn.eventId = "legacy-event";
      turn.tenantId = "tenant";
      turn.ownerId = "owner";
      turn.conversationId = "owner";
      turn.requestId = "request";
      turn.speaker = "agent";
      turn.occurredAt = Instant.now();
      turn.text = "retained history";
      session.persist(turn);
      transaction.commit();
      cursor = turn.cursor;
    }
    try (var connection = DriverManager.getConnection(url, "sa", "");
         var statement = connection.createStatement()) {
      statement.execute("ALTER TABLE approved_conversation_turn DROP COLUMN reviewRound");
    }
    try (var factory = factory(url, "update"); var session = factory.openSession()) {
      var restored = session.find(ApprovedTurnEntity.class, cursor);
      assertEquals(0, restored.reviewRound);
      assertEquals("retained history", restored.text);
    }
  }

  private SessionFactory factory(String url, String strategy) {
    return new Configuration().addAnnotatedClass(ApprovedTurnEntity.class)
        .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
        .setProperty("hibernate.connection.url", url)
        .setProperty("hibernate.connection.username", "sa")
        .setProperty("hibernate.connection.password", "")
        .setProperty("hibernate.hbm2ddl.auto", strategy)
        .buildSessionFactory();
  }
}
