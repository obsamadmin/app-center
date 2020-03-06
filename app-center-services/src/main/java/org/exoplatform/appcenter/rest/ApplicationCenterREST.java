package org.exoplatform.appcenter.rest;

import java.io.InputStream;
import java.util.List;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.*;

import org.exoplatform.appcenter.dto.*;
import org.exoplatform.appcenter.dto.Application;
import org.exoplatform.appcenter.service.*;
import org.exoplatform.common.http.HTTPStatus;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.services.security.ConversationState;

import io.swagger.annotations.*;

@Path("appCenter/applications")
@RolesAllowed("users")
@Api(value = "/appCenter/applications", description = "Manage and access application center applications") // NOSONAR
public class ApplicationCenterREST implements ResourceContainer {

  private static final Log         LOG = ExoLogger.getLogger(ApplicationCenterREST.class);

  private ApplicationCenterService appCenterService;

  public ApplicationCenterREST(ApplicationCenterService appCenterService) {
    this.appCenterService = appCenterService;
  }

  @POST
  @Path("/addApplication")
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("administrators")
  @ApiOperation(value = "Creates a new application in application center", httpMethod = "GET", response = Response.class, notes = "empty response")
  @ApiResponses(value = {
      @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
      @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
      @ApiResponse(code = 500, message = "Internal server error") })
  public Response createApplication(@ApiParam(value = "Application to save", required = true) Application application) {
    try {
      appCenterService.createApplication(application);
    } catch (ApplicationAlreadyExistsException e) {
      LOG.warn(e.getMessage());
      return Response.serverError().build();
    } catch (Exception e) {
      LOG.error("Unknown error occurred while creating application", e);
      return Response.serverError().build();
    }
    return Response.noContent().build();
  }

  @POST
  @Path("/editApplication")
  @RolesAllowed("administrators")
  @ApiOperation(value = "Updates an existing application identified by its id or title or url", httpMethod = "GET", response = Response.class, notes = "empty response")
  @ApiResponses(value = {
      @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
      @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
      @ApiResponse(code = 500, message = "Internal server error") })
  public Response updateApplication(@ApiParam(value = "Application to update", required = true) Application application) {
    try {
      appCenterService.updateApplication(application, getCurrentUserName());
    } catch (IllegalAccessException e) {
      LOG.warn(e.getMessage());
      return Response.status(HTTPStatus.UNAUTHORIZED).build();
    } catch (ApplicationAlreadyExistsException e) {
      LOG.warn(e);
      return Response.serverError().build();
    } catch (Exception e) {
      LOG.error("Unknown error occurred while updating application", e);
      return Response.serverError().build();
    }
    return Response.noContent().build();
  }

  @GET
  @Path("/deleteApplication/{applicationId}")
  @RolesAllowed("administrators")
  @ApiOperation(value = "Deletes an existing application identified by its id", httpMethod = "GET", response = Response.class, notes = "empty response")
  @ApiResponses(value = {
      @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
      @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
      @ApiResponse(code = 500, message = "Internal server error") })
  public Response deleteApplication(@ApiParam(value = "Application technical id to delete", required = true) @PathParam("applicationId") Long applicationId) {
    try {
      appCenterService.deleteApplication(applicationId, getCurrentUserName());
    } catch (IllegalAccessException e) {
      LOG.warn(e.getMessage());
      return Response.status(HTTPStatus.UNAUTHORIZED).build();
    } catch (ApplicationNotFoundException e) {
      LOG.warn(e.getMessage());
      return Response.serverError().build();
    } catch (Exception e) {
      LOG.error("Unknown error occurred while deleting application", e);
      return Response.serverError().build();
    }
    return Response.noContent().build();
  }

  @GET
  @Path("/addFavoriteApplication/{applicationId}")
  @RolesAllowed("users")
  @ApiOperation(value = "Adds an existing application identified by its id as favorite for current authenticated user", httpMethod = "GET", response = Response.class, notes = "empty response")
  @ApiResponses(value = {
      @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
      @ApiResponse(code = 500, message = "Internal server error") })
  public Response addFavoriteApplication(@ApiParam(value = "Application technical id to add as favorite", required = true) @PathParam("applicationId") Long applicationId) {
    try {
      appCenterService.addFavoriteApplication(applicationId, getCurrentUserName());
      return Response.noContent().build();
    } catch (IllegalAccessException e) {
      LOG.warn(e.getMessage());
      return Response.status(HTTPStatus.UNAUTHORIZED).build();
    } catch (ApplicationNotFoundException e) {
      LOG.warn(e.getMessage());
      return Response.serverError().build();
    } catch (Exception e) {
      LOG.error("Unknown error occurred while adding application as favorite", e);
      return Response.serverError().build();
    }
  }

  @GET
  @Path("/deleteFavoriteApplication/{applicationId}")
  @RolesAllowed("users")
  @ApiOperation(value = "Deletes an existing application identified by its id from current authenticated user favorites", httpMethod = "GET", response = Response.class, notes = "empty response")
  @ApiResponses(value = {
      @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
      @ApiResponse(code = 500, message = "Internal server error") })
  public Response deleteFavoriteApplication(@ApiParam(value = "Application technical id to delete from favorite", required = true) @PathParam("applicationId") Long applicationId) {
    try {
      appCenterService.deleteFavoriteApplication(applicationId, getCurrentUserName());
      return Response.noContent().build();
    } catch (Exception e) {
      LOG.error("Unknown error occurred while deleting application from favorites", e);
      return Response.serverError().build();
    }
  }

  @GET
  @Path("/setMaxFavorite")
  @RolesAllowed("administrators")
  @ApiOperation(value = "Modifies maximum application count to add as favorites for all users", httpMethod = "GET", response = Response.class, notes = "empty response")
  @ApiResponses(value = {
      @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
      @ApiResponse(code = 500, message = "Internal server error") })
  public Response setMaxFavoriteApps(@ApiParam(value = "Max favorites number", required = true) @QueryParam("number") long number) {
    try {
      appCenterService.setMaxFavoriteApps(number);
    } catch (Exception e) {
      LOG.error("Unknown error occurred while updating application", e);
      return Response.serverError().build();
    }
    return Response.noContent().build();
  }

  @POST
  @Path("/setDefaultImage")
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("administrators")
  @ApiOperation(value = "Modifies default application image setting", httpMethod = "GET", response = Response.class, notes = "empty response")
  @ApiResponses(value = {
      @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
      @ApiResponse(code = 500, message = "Internal server error") })
  public Response setDefaultAppImage(@ApiParam(value = "Application image id, body and name", required = true) ApplicationImage defaultAppImage) {
    try {
      appCenterService.setDefaultAppImage(defaultAppImage);
      return Response.noContent().build();
    } catch (Exception e) {
      LOG.error("Unknown error occurred while updating application", e);
      return Response.serverError().build();
    }
  }

  @GET
  @Path("/getGeneralSettings")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @ApiOperation(value = "Modifies default application image setting", httpMethod = "GET", response = Response.class, notes = "empty response")
  @ApiResponses(value = {
      @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
      @ApiResponse(code = 500, message = "Internal server error") })
  public Response getAppGeneralSettings() {
    try {
      GeneralSettings generalSettings = appCenterService.getAppGeneralSettings();
      return Response.ok(generalSettings).build();
    } catch (ApplicationNotFoundException e) {
      LOG.warn(e.getMessage());
      return Response.serverError().build();
    } catch (Exception e) {
      LOG.error("Unknown error occurred while updating application", e);
      return Response.serverError().build();
    }
  }

  @GET
  @Path("/getApplicationsList")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("administrators")
  @ApiOperation(value = "Retrieves all available applications", httpMethod = "GET", response = Response.class, produces = "application/json", notes = "Return list of applications in json format")
  @ApiResponses(value = {
      @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
      @ApiResponse(code = 500, message = "Internal server error") })
  public Response getApplicationsList(@ApiParam(value = "Query Offset", required = true) @QueryParam("offset") int offset,
                                      @ApiParam(value = "Query results limit", required = true) @QueryParam("limit") int limit,
                                      @ApiParam(value = "Keyword to search in applications title and url", required = true) @QueryParam("keyword") String keyword) {
    try {
      ApplicationList applicationList = appCenterService.getApplicationsList(offset,
                                                                             limit,
                                                                             keyword);
      return Response.ok(applicationList).build();
    } catch (Exception e) {
      LOG.error("Unknown error occurred while updating application", e);
      return Response.serverError().build();
    }
  }

  @GET
  @Path("/getAuthorizedApplicationsList")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @ApiOperation(value = "Retrieves all authorized applications for currently authenticated user", httpMethod = "GET", response = Response.class, produces = "application/json", notes = "Return list of applications in json format")
  @ApiResponses(value = {
      @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
      @ApiResponse(code = 500, message = "Internal server error") })
  public Response getAuthorizedApplicationsList(@ApiParam(value = "Query Offset", required = true) @QueryParam("offset") int offset,
                                                @ApiParam(value = "Query results limit", required = true) @QueryParam("limit") int limit,
                                                @ApiParam(value = "Keyword to search in applications title and url", required = true) @QueryParam("keyword") String keyword) {

    try {
      ApplicationList applicationList = appCenterService.getAuthorizedApplicationsList(offset,
                                                                                       limit,
                                                                                       keyword,
                                                                                       getCurrentUserName());
      return Response.ok(applicationList).build();
    } catch (Exception e) {
      LOG.error("Unknown error occurred while updating application", e);
      return Response.serverError().build();
    }
  }

  @GET
  @Path("/getFavoriteApplicationsList")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @ApiOperation(value = "Retrieves favorite applications for currently authenticated user", httpMethod = "GET", response = Response.class, produces = "application/json", notes = "Return list of applications in json format")
  @ApiResponses(value = {
      @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
      @ApiResponse(code = 500, message = "Internal server error") })
  public Response getFavoriteApplicationsList() {
    try {
      List<UserApplication> applications = appCenterService.getFavoriteApplicationsList(getCurrentUserName());
      return Response.ok(applications).build();
    } catch (Exception e) {
      LOG.error("Unknown error occurred while updating application", e);
      return Response.serverError().build();
    }
  }

  @GET
  @Path("/illustration/{applicationId}")
  @RolesAllowed("users")
  @ApiOperation(value = "Gets an application illustration by application id", httpMethod = "GET", response = Response.class, notes = "This can only be done by the logged in user.")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Request fulfilled"),
      @ApiResponse(code = 500, message = "Internal server error"),
      @ApiResponse(code = 400, message = "Invalid query input"),
      @ApiResponse(code = 404, message = "Resource not found") })
  public Response getApplicationIllustration(@Context Request request,
                                             @ApiParam(value = "Application id", required = true) @PathParam("applicationId") long applicationId) {
    try {
      Long lastUpdated = appCenterService.getApplicationImageLastUpdated(applicationId, getCurrentUserName());
      if (lastUpdated == null) {
        return Response.status(404).build();
      }
      EntityTag eTag = new EntityTag(Integer.toString(lastUpdated.hashCode()));
      Response.ResponseBuilder builder = request.evaluatePreconditions(eTag);
      if (builder == null) {
        InputStream stream = appCenterService.getApplicationImageInputStream(applicationId, getCurrentUserName());
        if (stream == null) {
          return Response.status(404).build();
        }
        /*
         * As recommended in the the RFC1341
         * (https://www.w3.org/Protocols/rfc1341/4_Content-Type.html), we set
         * the avatar content-type to "image/png". So, its data would be
         * recognized as "image" by the user-agent.
         */
        builder = Response.ok(stream, "image/png");
        builder.tag(eTag);
      }
      CacheControl cc = new CacheControl();
      cc.setMaxAge(86400);
      builder.cacheControl(cc);
      return builder.cacheControl(cc).build();
    } catch (IllegalAccessException e) {
      LOG.warn(e.getMessage());
      return Response.status(HTTPStatus.UNAUTHORIZED).build();
    } catch (ApplicationNotFoundException e) {
      LOG.warn(e.getMessage());
      return Response.serverError().build();
    } catch (Exception e) {
      LOG.error("Unknown error occurred while getting application illustration", e);
      return Response.serverError().build();
    }
  }

  private String getCurrentUserName() {
    ConversationState state = ConversationState.getCurrent();
    return state == null || state.getIdentity() == null ? null
                                                        : state.getIdentity()
                                                               .getUserId();
  }

}
