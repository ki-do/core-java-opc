package eu.arrowhead.core.orchestrator;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import eu.arrowhead.common.configuration.SysConfig;
import eu.arrowhead.common.model.ArrowheadService;
import eu.arrowhead.common.model.ArrowheadSystem;
import eu.arrowhead.common.model.messages.AuthorizationRequest;
import eu.arrowhead.common.model.messages.AuthorizationResponse;
import eu.arrowhead.common.model.messages.GSDEntry;
import eu.arrowhead.common.model.messages.GSDRequestForm;
import eu.arrowhead.common.model.messages.GSDResult;
import eu.arrowhead.common.model.messages.ICNRequestForm;
import eu.arrowhead.common.model.messages.ICNResultForm;
import eu.arrowhead.common.model.messages.IntraCloudAuthRequest;
import eu.arrowhead.common.model.messages.IntraCloudAuthResponse;
import eu.arrowhead.common.model.messages.OrchestrationForm;
import eu.arrowhead.common.model.messages.OrchestrationResponse;
import eu.arrowhead.common.model.messages.ProvidedService;
import eu.arrowhead.common.model.messages.QoSReservationResponse;
import eu.arrowhead.common.model.messages.QoSReserve;
import eu.arrowhead.common.model.messages.QoSVerificationResponse;
import eu.arrowhead.common.model.messages.QoSVerify;
import eu.arrowhead.common.model.messages.ServiceQueryForm;
import eu.arrowhead.common.model.messages.ServiceQueryResult;
import eu.arrowhead.common.model.messages.ServiceRequestForm;

/**
 * @author pardavib, mereszd
 *
 */

public class OrchestratorService {

	private URI uri;
	private Client client;
	private ServiceRequestForm serviceRequestForm;
	public SysConfig sysConfig = SysConfig.getInstance();

	public OrchestratorService() {
		super();
		uri = null;
		client = ClientBuilder.newClient();
	}

	public OrchestratorService(ServiceRequestForm serviceRequestForm) {
		super();
		uri = null;
		client = ClientBuilder.newClient();
		this.serviceRequestForm = serviceRequestForm;
	}

	/**
	 * This function represents the local orchestration process.
	 */
	public OrchestrationResponse localOrchestration() {
		ServiceQueryForm srvQueryForm = new ServiceQueryForm(this.serviceRequestForm);
		srvQueryForm.setTsig_key("RIuxP+vb5GjLXJo686NvKQ==");
		ServiceQueryResult srvQueryResult;
		IntraCloudAuthRequest authReq;
		IntraCloudAuthResponse authResp;
		List<ArrowheadSystem> providers = new ArrayList<ArrowheadSystem>();
		List<ProvidedService> provservices = new ArrayList<ProvidedService>();
		QoSVerify qosVerification;
		QoSVerificationResponse qosVerificationResponse;
		ArrowheadSystem selectedSystem = null;
		QoSReserve qosReservation;
		QoSReservationResponse qosReservationResponse;
		List<OrchestrationForm> responseFormList = new ArrayList<OrchestrationForm>();

		// Poll the Service Registry
		srvQueryResult = getServiceQueryResult(srvQueryForm, this.serviceRequestForm);
		provservices = srvQueryResult.getServiceQueryData();
		for (ProvidedService providedService : provservices) {
			providers.add(providedService.getProvider());
		}
		//If the SRF is external, no need for Auth and QoS
		if (isExternal() == false){
			
			//Poll the Authorization
			authReq = new IntraCloudAuthRequest("authInfo", this.serviceRequestForm.getRequestedService(), false, providers);
			authResp = getAuthorizationResponse(authReq, this.serviceRequestForm);
			
			//Removing the non-authenticated systems from the providers list
			for (ArrowheadSystem ahsys : authResp.getAuthorizationMap().keySet()){
				if(authResp.getAuthorizationMap().get(ahsys) == false)
					providers.remove(ahsys);
			}
			
			// Poll the QoS Service
			qosVerification = new QoSVerify(serviceRequestForm.getRequesterSystem(),
					serviceRequestForm.getRequestedService(), providers, "RequestedQoS");
			qosVerificationResponse = getQosVerificationResponse(qosVerification);
			
			//Removing the bad QoS ones from consideration - temporarly everything is true
			for (ArrowheadSystem ahsys : qosVerificationResponse.getResponse().keySet()){
				if (qosVerificationResponse.getResponse().get(ahsys) == false)
					providers.remove(ahsys);
			}
			
			// Reserve QoS resources
			selectedSystem = providers.get(0); //temporarily selects the first fit System
			qosReservation = new QoSReserve(selectedSystem, this.serviceRequestForm.getRequesterSystem(), "requestedQoS", this.serviceRequestForm.getRequestedService());
			qosReservationResponse = doQosReservation(qosReservation);
			
			//Actualizing the provservices list
			for (ProvidedService provservice : provservices){
				if(providers.contains(provservice.getProvider()) == false)
					provservices.remove(provservice);
			}
		}
		// Create Orchestration Forms - returning every matching System as Required for the Demo
		for (ProvidedService provservice : provservices){
			responseFormList.add(new OrchestrationForm(this.serviceRequestForm.getRequestedService(), provservice.getProvider(), provservice.getServiceURI(), "authorizationInfo"));
		}
		return new OrchestrationResponse(responseFormList);
	}

	/**
	 * This function represents the Intercloud orchestration process.
	 */
	public OrchestrationResponse intercloudOrchestration() {
		GSDRequestForm gsdRequestForm;
		GSDResult gsdResult;
		ICNRequestForm icnRequestForm;
		ICNResultForm icnResultForm;
		OrchestrationForm orchForm;
		OrchestrationResponse orchResponse;
		ArrayList<OrchestrationForm> responseFormList = new ArrayList<OrchestrationForm>();

		// Init Global Service Discovery
		gsdRequestForm = new GSDRequestForm(serviceRequestForm.getRequestedService());
		gsdResult = getGSDResult(gsdRequestForm);

		// Putting an ICN Request Form to every single matching cloud
		for (GSDEntry entry : gsdResult.getResponse()){
			//ICN Request for each cloud contained in an Entry
			icnResultForm = getICNResultForm(new ICNRequestForm(this.serviceRequestForm.getRequestedService(), "authenticationInfo", entry.getCloud()));
			//Adding every OrchestrationForm from the returned Response to the final Response
			for (OrchestrationForm of : icnResultForm.getInstructions().getResponse()){
				responseFormList.add(of);
			}
		}
		
		//Creating the response
		orchResponse = new OrchestrationResponse(responseFormList);
		
		// Return orchestration form
		return orchResponse;
	}

	/**
	 * Sends the Service Query Form to the Service Registry and asks for the
	 * Service Query Result.
	 * 
	 * @param sqf
	 * @param srForm
	 * @return ServiceQueryResult
	 */
	private ServiceQueryResult getServiceQueryResult(ServiceQueryForm sqf, ServiceRequestForm srf) {
		System.out.println("orchestator: inside the getServiceQueryResult function");
		ArrowheadService as = srf.getRequestedService();
		String strtarget = sysConfig.getServiceRegistryURI()+ "/" + as.getServiceGroup() + "/" + as.getServiceDefinition();
		System.out.println("orchestrator: sending the ServiceQueryForm to this address:" + strtarget);
		WebTarget target = client.target(strtarget);
		Response response = target.request().header("Content-type", "application/json").put(Entity.json(sqf));
		ServiceQueryResult sqr = response.readEntity(ServiceQueryResult.class);
		System.out.println("orchestrator received the following services from the SR:");
		for (ProvidedService providedService : sqr.getServiceQueryData()) {
			System.out.println(providedService.getProvider().getSystemGroup() + providedService.getProvider().getSystemName());
		}
		return sqr;
	}

	/**
	 * Sends the Authorization Request to the Authorization service and asks for
	 * the Authorization Response.
	 * 
	 * @param authRequest
	 * @return AuthorizationResponse
	 */
	private IntraCloudAuthResponse getAuthorizationResponse(IntraCloudAuthRequest authReq, ServiceRequestForm srf) {	
		System.out.println("orchestrator: inside the getAuthorizationResponse function");
		String strtarget = sysConfig.getAuthorizationURI() + "/SystemGroup/" + srf.getRequesterSystem().getSystemGroup() +
				"/System/" + srf.getRequesterSystem().getSystemName();
		System.out.println("orchestrator: sending AuthReq to this address: " + strtarget);
		WebTarget target = client.target(strtarget);
		Response response = target.request().header("Content-type","application/json").put(Entity.json(authReq));
		IntraCloudAuthResponse authResp = response.readEntity(IntraCloudAuthResponse.class);
		System.out.println("orchestrator received the following services from the AR:");
		for (ArrowheadSystem ahsys : authResp.getAuthorizationMap().keySet()){
			System.out.println("System: " + ahsys.getSystemGroup() + ahsys.getSystemName());
			System.out.println("Value: " + authResp.getAuthorizationMap().get(ahsys));
		}
		return authResp;
	}

	/**
	 * Sends the QoS Verify message to the QoS service and asks for the QoS
	 * Verification Response.
	 * 
	 * @param qosVerify
	 * @return QoSVerificationResponse
	 */
	private QoSVerificationResponse getQosVerificationResponse(QoSVerify qosVerify) {
		System.out.println("orchestrator: inside the getQoSVerificationResponse function");
		//TODO: get address from database
		//String strtarget = "http://localhost:8080/core/QoSManager/QoSVerify";
		String strtarget = sysConfig.getOrchestratorURI().replace("orchestration", "QoSManager") + "/QoSVerify";
		WebTarget target = client.target(strtarget);
		Response response = target.request().header("Content-type", "application/json").put(Entity.json(qosVerify));
		return response.readEntity(QoSVerificationResponse.class);
	}

	/**
	 * Sends QoS reservation to the QoS service.
	 * 
	 * @param qosReserve
	 * @return boolean indicating that the reservation completed successfully
	 */
	private QoSReservationResponse doQosReservation(QoSReserve qosReserve) {
		System.out.println("orchestrator: inside the doQoSReservation function");
		//TODO: get address from database
		String strtarget = sysConfig.getOrchestratorURI().replace("orchestration", "QoSManager") + "/QoSReserve";
		//String strtarget = "http://localhost:8080/core/QoSManager/QoSReserve";
		WebTarget target = client.target(strtarget);
		Response response = target.request().header("Content-type", "application/json").put(Entity.json(qosReserve));
		return response.readEntity(QoSReservationResponse.class);
	}

	/**
	 * Initiates the Global Service Discovery process by sending a
	 * GSDRequestForm to the Gatekeeper service and fetches the results.
	 * 
	 * @param gsdRequestForm
	 * @return GSDResult
	 */
	private GSDResult getGSDResult(GSDRequestForm gsdRequestForm) {
		String strtarget = sysConfig.getGatekeeperURI()+"/init_gsd";
		WebTarget target = client.target(strtarget);
		Response response = target.request().header("Content-type", "application/json")
				.put(Entity.json(gsdRequestForm));
		return response.readEntity(GSDResult.class);
	}

	/**
	 * Initiates the Inter-Cloud Negotiation process by sending an
	 * ICNRequestForm to the Gatekeeper service and fetches the results.
	 * 
	 * @param icnRequestForm
	 * @return ICNResultForm
	 */
	private ICNResultForm getICNResultForm(ICNRequestForm icnRequestForm) {
		String strtarget = sysConfig.getGatekeeperURI()+"/init_icn";
		WebTarget target = client.target(strtarget);
		Response response = target.request().header("Content-type", "application/json")
				.put(Entity.json(icnRequestForm));
		return response.readEntity(ICNResultForm.class);
	}

	/**
	 * Returns if Inter-Cloud Orchestration is required or not based on the
	 * Service Request Form.
	 * 
	 * @return Boolean
	 */
	public Boolean isInterCloud() {
		return this.serviceRequestForm.getOrchestrationFlags().get("TriggerInterCloud");
	}
	
	public Boolean isExternal (){
		return this.serviceRequestForm.getOrchestrationFlags().get("ExternalServiceRequest");
	}

}
