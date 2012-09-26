/******************************************************************************* 
 * This program is made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: 
 * camunda services GmbH - initial API and implementation 
 *
 ******************************************************************************/
package org.eclipse.bpmn2.modeler.core.importer.handlers;

import org.eclipse.bpmn2.BaseElement;
import org.eclipse.bpmn2.di.BPMNShape;
import org.eclipse.bpmn2.modeler.core.Activator;
import org.eclipse.bpmn2.modeler.core.importer.Bpmn2ModelImport;
import org.eclipse.bpmn2.modeler.core.preferences.ShapeStyle;
import org.eclipse.bpmn2.modeler.core.utils.GraphicsUtil;
import org.eclipse.bpmn2.modeler.core.utils.GraphicsUtil.Size;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.dd.dc.Bounds;
import org.eclipse.graphiti.datatypes.ILocation;
import org.eclipse.graphiti.features.IAddFeature;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.context.impl.AddContext;
import org.eclipse.graphiti.features.context.impl.AreaContext;
import org.eclipse.graphiti.mm.pictograms.ContainerShape;
import org.eclipse.graphiti.mm.pictograms.Diagram;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.services.Graphiti;

/**
 * 
 * @author Nico Rehwaldt
 * @author Daniel Meyer
 * 
 */
public abstract class AbstractShapeHandler<T extends BaseElement> {
	
	protected Bpmn2ModelImport bpmn2ModelImport;
	protected IFeatureProvider featureProvider;
	protected Diagram diagram;

	public AbstractShapeHandler(Bpmn2ModelImport bpmn2ModelImport) {
		this.bpmn2ModelImport = bpmn2ModelImport;
		featureProvider = bpmn2ModelImport.getFeatureProvider();
		diagram = bpmn2ModelImport.getDiagram();
	}
	
	/**
	 * Find a Graphiti feature for given shape and generate necessary diagram elements.
	 * 
	 */
	public PictogramElement handleShape(T bpmnElement, BPMNShape shape, ContainerShape container) {

		// TODO: WTF is this??
//		if (shape.getChoreographyActivityShape() != null) {
//			// FIXME: we currently generate participant bands automatically
//			return;
//		}
		
		AddContext context = createAddContext(bpmnElement);		
		IAddFeature addFeature = createAddFeature(context);
		
		if (addFeature != null) {

			// TODO: WTF is this??
			//	context.putProperty(IMPORT_PROPERTY, true);

			setSize(context, shape, bpmnElement);
				
			addToTargetContainer(context, container);
			
			setLocation(context, container, shape);
			
	
			if (addFeature.canAdd(context)) {
				PictogramElement newElement = createPictogramElement(context, addFeature);
				createLink(bpmnElement, shape, newElement);
				return newElement;
			} else { 
				Activator.logStatus(new Status(IStatus.WARNING, Activator.PLUGIN_ID, "Add feature cannot add context: "+ addFeature));
				return null;
			}
			
		} else  {
			Activator.logStatus(new Status(IStatus.WARNING, Activator.PLUGIN_ID, "Element not supported: "
					+ bpmnElement.eClass().getName()));
			
			return null;
			
		}
			
			
//				if (bpmnElement instanceof Participant) {
					// TODO: figure out why this was put here initially;
					// participant bands are already handled separately
	//				elements.put(((Participant) bpmnElement).getProcessRef(), newContainer);
//				}
//				else if (bpmnElement instanceof ChoreographyActivity) {
//					ChoreographyActivity ca = (ChoreographyActivity)bpmnElement;
//					for (PictogramElement pe : ((ContainerShape)newContainer).getChildren()) {
//						Object o = Graphiti.getLinkService().getBusinessObjectForLinkedPictogramElement(pe);
//						if (o instanceof Participant)
//							elements.put((Participant)o, pe);
//					}
//				}
	//			else if (bpmnElement instanceof Event) {
	//				GraphicsUtil.setEventSize(context.getWidth(), context.getHeight(), diagram);
	//			} else if (bpmnElement instanceof Gateway) {
	//				GraphicsUtil.setGatewaySize(context.getWidth(), context.getHeight(), diagram);
	//			} else if (bpmnElement instanceof Activity && !(bpmnElement instanceof SubProcess)) {
	//				GraphicsUtil.setActivitySize(context.getWidth(), context.getHeight(), diagram);
	//			}
				
//				elements.put(bpmnElement, newContainer);
//				handleEvents(bpmnElement, newContainer);
//			}
//		ModelUtil.addID(bpmnElement);
	}



	protected void setLocation(AddContext context, ContainerShape container, BPMNShape shape) {
		
		Bounds bounds = shape.getBounds();
		
		int x = (int) bounds.getX();
		int y = (int) bounds.getY();
		
		ILocation loc = Graphiti.getPeLayoutService().getLocationRelativeToDiagram(container);
		x -= loc.getX();
		y -= loc.getY();
		
		context.setLocation(x, y);
		
	}

	protected void addToTargetContainer(AddContext context, ContainerShape container) {
		context.setTargetContainer(container);
	}

	protected void setSize(AddContext context, BPMNShape shape, T bpmnElement) {

		ShapeStyle ss = bpmn2ModelImport.getPreferences().getShapeStyle(bpmnElement);
		
		boolean useDefaultSize = false;
		if (ss!=null) {
			useDefaultSize = ss.isDefaultSize();
		}
	
		if (useDefaultSize) {
			Size size = GraphicsUtil.getShapeSize(bpmnElement, diagram);
			if (size != null) {
				setDefaultSize(context, size);
			} else {
				setSizeFromShapeBounds(context, shape);
			}	
		} else {
			setSizeFromShapeBounds(context, shape);
		}
		
	}

	private void setDefaultSize(AddContext context, Size size) {
		context.setSize(size.getWidth(),size.getHeight());
	}

	protected void setSizeFromShapeBounds(AddContext context, BPMNShape shape) {
		Bounds bounds = shape.getBounds();
		
		int width = (int) bounds.getWidth();
		int height = (int) bounds.getHeight();
		
		context.setSize(width, height);		
	}

	protected IAddFeature createAddFeature(AddContext context) {
		return featureProvider.getAddFeature(context);
	}

	protected AddContext createAddContext(BaseElement bpmnElement) {
		AddContext context = new AddContext(new AreaContext(), bpmnElement);
		// FIXME: why??
//		context.setNewObject(bpmnElement);
		return context;
	}
	
	protected PictogramElement createPictogramElement(AddContext context, IAddFeature addFeature) {
		return addFeature.add(context);
	}

	protected void createLink(T bpmnElement, BPMNShape shape, PictogramElement newContainer) {
		featureProvider.link(newContainer, new Object[] { bpmnElement, shape });
	}
}