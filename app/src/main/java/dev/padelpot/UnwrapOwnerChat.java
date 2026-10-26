package dev.padelpot;

import dev.padelpot.domain.ChatIngress;
import dev.padelpot.domain.OwnerAdmission;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.service.ReactiveService;

@ApplicationScoped
public class UnwrapOwnerChat implements ReactiveService<OwnerAdmission, ChatIngress> {
  @Override
  public Uni<ChatIngress> process(OwnerAdmission admission) {
    if (!(admission instanceof OwnerAdmission.Chat chat))
      throw new IllegalArgumentException("Only a new message can enter owner conversation");
    return Uni.createFrom().item(chat.value());
  }
}
