package dev.padelpot;

import jakarta.annotation.Priority;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.*;
import java.util.Map;

@Provider
@Priority(1)
public class ChannelErrorMapper implements ExceptionMapper<WebApplicationException> {
  public Response toResponse(WebApplicationException e) {
    return Response.status(e.getResponse().getStatus())
        .entity(Map.of("error", e.getMessage() == null ? "Request rejected" : e.getMessage()))
        .build();
  }
}
