package eu.arrowhead.core.authorization.database;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

@Entity
public class ArrowheadService {

	@Id @GeneratedValue
	private int id;
	private String serviceGroup;
	private String serviceDefinition;
	@ElementCollection
	private List<String> interfaces = new ArrayList<String>();
	private String metaData;
	
	public ArrowheadService(){
		
	}
	
	public ArrowheadService(String serviceGroup, String serviceDefinition,
			List<String> interfaces, String metaData) {
		super();
		this.serviceGroup = serviceGroup;
		this.serviceDefinition = serviceDefinition;
		this.interfaces = interfaces;
		this.metaData = metaData;
	}

	public String getServiceGroup() {
		return serviceGroup;
	}

	public void setServiceGroup(String serviceGroup) {
		this.serviceGroup = serviceGroup;
	}

	public String getServiceDefinition() {
		return serviceDefinition;
	}

	public void setServiceDefinition(String serviceDefinition) {
		this.serviceDefinition = serviceDefinition;
	}

	public List<String> getInterfaces() {
		return interfaces;
	}

	public void setInterfaces(List<String> interfaces) {
		this.interfaces = interfaces;
	}

	public String getMetaData() {
		return metaData;
	}

	public void setMetaData(String metaData) {
		this.metaData = metaData;
	}
	
	
}
