package de.tu_dresden.lat.api;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


@Path("/repair")
@Produces(MediaType.APPLICATION_JSON)
public class ElExplicatorResources {
    private final RepairSession repairSession;

    public ElExplicatorResources(RepairSession repairSession) {
        this.repairSession = repairSession;
    }

    @GET 
    @Path("/decisiontree")
    public Response getDecisionTree(){
        List<Map<String, Object>> decisionTree = repairSession.getDecisionTree();
        return Response.ok(decisionTree).build();
    }

    @GET
    @Path("/{id}")
    public Response getNodeInfo(@PathParam("id") long id) {
        AxiomNode node = repairSession.getNodeById(id);
        if (node == null){
            return Response.status(Response.Status.NOT_FOUND)
                       .entity(new ErrorResponse("Node not found!"))
                       .build();
        } 
        InfoResponse response = new InfoResponse(node);
        return Response.ok(response).build();
    }

    @GET
    @Path("/{id}/impact1")
    public Response getImpact1ByID(@PathParam("id") long id) {
        ImpactResponse impact = repairSession.getProbabilityImpact(id);
        if (impact == null){
            return Response.status(Response.Status.NOT_FOUND)
                       .entity(new ErrorResponse("Impact for node not found!"))
                       .build();
        }
        return Response.ok(impact).build();
    }

    @GET
    @Path("/{id}/impact2")
    public Response getImpact2ByID(@PathParam("id") long id) {
        ImpactResponse impact = repairSession.getHierarchyImpact(id);
        if (impact == null){
            return Response.status(Response.Status.NOT_FOUND)
                       .entity(new ErrorResponse("Impact for node not found!"))
                       .build();
        }
        return Response.ok(impact).build();
    }

    @GET
    @Path("/{id}/impact3")
    public Response getImpact3ByID(@PathParam("id") long id) {
        ImpactResponse impact = repairSession.getHammingImpact(id);
        if (impact == null){
            return Response.status(Response.Status.NOT_FOUND)
                       .entity(new ErrorResponse("Impact for node not found!"))
                       .build();
        }
        return Response.ok(impact).build();
    }

    @POST 
    @Path("/{id}/save")
    public Response saveOntology(@PathParam("id") long id, @QueryParam ("filename") String filename) {
        SaveResponse saveStatus;
        try{
            saveStatus = repairSession.saveOntology(id, filename);
        } catch (NodeNotFoundException e){
            return Response.status(Response.Status.NOT_FOUND)
                       .entity(new ErrorResponse(e.getMessage()))
                       .build();
        } catch (RuntimeException e){
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                       .entity(new ErrorResponse(e.getMessage()))
                       .build();
        }
        return Response.ok(saveStatus).build();
    }

    @POST
    @Path("/{id}/save-anyway")
    public Response saveOntologyAndContinue(@PathParam("id") long id, @QueryParam ("filename") String filename) {
        try{
            repairSession.saveAnyway(id, filename);
        }
        catch (NodeNotFoundException e){
            return Response.status(Response.Status.NOT_FOUND)
                       .entity(new ErrorResponse(e.getMessage()))
                       .build();
        }
        catch (RuntimeException e){
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                       .entity(new ErrorResponse(e.getMessage()))
                       .build();
        }
        return Response.ok().build();
    }
}
