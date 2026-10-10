package tech.kayys.aqli.server.api.v1;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import tech.kayys.aqli.server.SdkProvider;
import tech.kayys.aqli.sdk.model.SystemInfo;

@Path("/v1/system")
public class SystemResource {

    @Inject
    SdkProvider sdkProvider;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSystemInfo() {
        try {
            var sdk = sdkProvider.getSdk();
            SystemInfo info = sdk.getSystemInfo();
            return Response.ok(info).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(java.util.Map.of("error", e.getMessage())).build();
        }
    }
}
