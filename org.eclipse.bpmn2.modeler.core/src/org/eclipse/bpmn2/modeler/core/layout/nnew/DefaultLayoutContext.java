package org.eclipse.bpmn2.modeler.core.layout.nnew;

import static org.eclipse.bpmn2.modeler.core.layout.util.ConversionUtil.location;
import static org.eclipse.bpmn2.modeler.core.layout.util.ConversionUtil.point;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.bpmn2.BoundaryEvent;
import org.eclipse.bpmn2.modeler.core.layout.AnchorPointStrategy;
import org.eclipse.bpmn2.modeler.core.layout.BendpointStrategy;
import org.eclipse.bpmn2.modeler.core.layout.Docking;
import org.eclipse.bpmn2.modeler.core.layout.LayoutStrategy;
import org.eclipse.bpmn2.modeler.core.layout.util.ConversionUtil;
import org.eclipse.bpmn2.modeler.core.layout.util.LayoutUtil;
import org.eclipse.bpmn2.modeler.core.layout.util.LayoutUtil.Sector;
import org.eclipse.bpmn2.modeler.core.utils.BusinessObjectUtil;
import org.eclipse.bpmn2.modeler.core.utils.Tuple;
import org.eclipse.graphiti.datatypes.ILocation;
import org.eclipse.graphiti.datatypes.IRectangle;
import org.eclipse.graphiti.mm.algorithms.styles.Point;
import org.eclipse.graphiti.mm.pictograms.Anchor;
import org.eclipse.graphiti.mm.pictograms.FreeFormConnection;
import org.eclipse.graphiti.mm.pictograms.Shape;

public class DefaultLayoutContext implements LayoutContext {

	private FreeFormConnection connection;

	private List<ConnectionPart> connectionParts;
	
	private Set<ConnectionPart> brokenConnectionParts;
	
	private Anchor startAnchor;
	private Anchor endAnchor;
	
	private Shape startShape;
	private Shape endShape;
	
	private IRectangle startShapeBounds;
	private IRectangle endShapeBounds;
	
	private List<Point> connectionPoints;

	public DefaultLayoutContext(FreeFormConnection connection) {
		this.connection = connection;
		
		initSourceAndTarget(connection.getStart(), connection.getEnd());
		
		recomputePointsAndParts();
	}

	private void initSourceAndTarget(Anchor start, Anchor end) {
		startAnchor = start;
		endAnchor = end;

		startShape = (Shape) start.getParent();
		endShape = (Shape) end.getParent();

		startShapeBounds = LayoutUtil.getAbsoluteRectangle(startShape);
		endShapeBounds = LayoutUtil.getAbsoluteRectangle(endShape);
	}

	protected void computeConnectionParts() {
		
		// compute connection points
		List<Point> points = connectionPoints;
		
		List<ConnectionPart> parts = new ArrayList<ConnectionPart>();
		Set<ConnectionPart> brokenParts = new HashSet<ConnectionPart>();
		
		Point current = points.get(0);
		
		for (int i = 1; i < points.size(); i++) {
			Point p = points.get(i);
			
			ConnectionPart part = new ConnectionPart(current, p);
			
			parts.add(part);
			
			if (part.needsLayout()) {
				brokenParts.add(part);
			}
			
			current = p;
		}
		
		this.brokenConnectionParts = brokenParts;
		this.connectionParts = parts;
	}

	protected void computeConnectionPoints() {
		ArrayList<Point> points = new ArrayList<Point>();

		ILocation startLocation = LayoutUtil.getAnchorLocation(startAnchor);
		ILocation endLocation = LayoutUtil.getAnchorLocation(endAnchor);
		
		points.add(point(startLocation));
		
		// assumption: we have only two anchors (start and end)
		for (Point bendpoint: connection.getBendpoints()) {
			points.add(bendpoint);
		}
		
		points.add(point(endLocation));
		
		this.connectionPoints = points;
	}
	
	
	@Override
	public boolean isRepairable() {
		if (brokenConnectionParts.isEmpty()) {
			return true;
		} 
		
		if (connection.getBendpoints().isEmpty()) {
			
			// nothing to repair
			return false;
		} else {
			if (brokenConnectionParts.size() > 1) {
				
				// too many broken parts
				return false;
			} else {
				// only one connection part is broken --> layout change
				return true;
			}
		}
	}
	
	@Override
	public boolean repair() {
		List<ConnectionPart> parts = connectionParts;
		
		boolean repaired = true;
		
		if (parts.get(0).needsLayout()) {
			repaired = repaired && layoutConnectionParts(parts, true);
		}
		
		repaired = repaired && fixAnchor(startShape, startAnchor, true);
		
		if (parts.get(parts.size() - 1).needsLayout()) {
			repaired = repaired && layoutConnectionParts(parts, false);
		}
		
		repaired = repaired && fixAnchor(endShape, endAnchor, false);
		
		return repaired && isConnectionRepaired();
	}

	private boolean fixAnchor(Shape targetShape, Anchor targetShapeAnchor, boolean start) {
		
		// TODO: Do not blindly unset custom anchors
		// instead try to retain them
		
		Anchor centerAnchor = LayoutUtil.getCenterAnchor(targetShape);
		
		// no fix required for center anchor
		if (targetShapeAnchor.equals(centerAnchor)) {
			return true;
		}
		
		// set center anchor
			
		if (start) {
			setNewStartAnchor(centerAnchor);
		} else {
			setNewEndAnchor(centerAnchor);
		}
		
		return repair();
	}
	
	private void recomputePointsAndParts() {
		computeConnectionPoints();
		computeConnectionParts();
	}
	
	private void setNewStartAnchor(Anchor anchor) {
		this.startAnchor = anchor;
		this.connection.setStart(anchor);
		
		recomputePointsAndParts();
	}

	private void setNewEndAnchor(Anchor anchor) {
		this.endAnchor = anchor;
		this.connection.setEnd(anchor);
		
		recomputePointsAndParts();
	}

	protected boolean layoutConnectionParts(List<ConnectionPart> parts, boolean start) {
		ConnectionPart next;
		ConnectionPart part;
		
		if (parts.size() < 2) {
			return false;
		}
		
		if (start) {
			part = parts.get(0);
			next = parts.get(1);
		} else {
			part = parts.get(parts.size() - 1);
			next = parts.get(parts.size() - 2);
		}
		
		if (next.needsLayout()) {
			return false;
		}
		
		Direction direction = part.computeDirection(next);
		
		Point repairCandidate = part.getRepairCandidate(start);
		Point reference = part.getReference(start);
		
		switch (direction) {
		case HORIZONTAL: 
			repairCandidate.setY(reference.getY());
			break;
		case VERTICAL:
			repairCandidate.setX(reference.getX());
			break;
		}
		
		return true;
	}

	protected boolean isConnectionRepaired() {
		
		boolean repaired = true;
		
		List<Point> points = connectionPoints;
		
		Point firstBendpoint = points.get(1);
		Point lastBendpoint = points.get(points.size() - 2);

		repaired &= !LayoutUtil.isContained(startShapeBounds, location(firstBendpoint), 13);
		
		repaired &= !LayoutUtil.isContained(endShapeBounds, location(lastBendpoint), 13);
		
		// see if anchor point is on start shape bounds
		repaired &= (startShapeBounds.getX() != firstBendpoint.getX() || startShapeBounds.getX() + startShapeBounds.getWidth() != firstBendpoint.getX());
		repaired &= (startShapeBounds.getY() != firstBendpoint.getY() || startShapeBounds.getY() + startShapeBounds.getHeight() != firstBendpoint.getY());
		
		Tuple<Docking, Docking> dockings = getDockings();
		if (dockings != null && BusinessObjectUtil.getFirstBaseElement(startShape) instanceof BoundaryEvent) {
			Sector afterRepairSector = LayoutUtil.getSector(ConversionUtil.location(firstBendpoint), LayoutUtil.getAbsoluteRectangle(startShape));
			repaired &=  afterRepairSector == dockings.getFirst().getSector(); 
		}
		
		return repaired;
	}

	@Override
	public void layout() {
		layoutBendpoints(layoutAnchors());
	}
	
	public Tuple<Docking, Docking> layoutAnchors() {
		AnchorPointStrategy anchorPointStrategy = AnchorPointStrategy.build(AnchorPointStrategy.class, connection, null);
		return anchorPointStrategy.execute();
	}
	
	public void layoutBendpoints(Tuple<Docking, Docking> connectionDocking) {
		// change bendpoints only when anchor points were applied
		if (connectionDocking != null) {
			LayoutStrategy.build(BendpointStrategy.class, connection, connectionDocking).execute();
		}
	}
	
	public Tuple<Docking, Docking> getDockings() {
		return AnchorPointStrategy.build(AnchorPointStrategy.class, connection, null).getDockings();
	}

	/**
	 * Enum indicating the layed out direction of a connection part in a layouted connection
	 * 
	 * @author nico.rehwaldt
	 */
	protected static enum Direction {
		
		VERTICAL, 
		HORIZONTAL, 
		UNSPECIFIED, 
		UNKNOWN;
		
		public Direction inverse() {
			switch (this) {
			case VERTICAL: 
				return HORIZONTAL;
			case HORIZONTAL:
				return VERTICAL;
			default: 
				return this;
			}
		}
	}
	
	/**
	 * Connection part
	 * 
	 * @author nico.rehwaldt
	 */
	protected class ConnectionPart {
		
		private Point first;
		private Point second;
		
		public ConnectionPart(Point first, Point second) {
			this.first = first;
			this.second = second;
		}
		
		/**
		 * Compute own direction based on the next connection part
		 * 
		 * @param next
		 * @return
		 */
		public Direction computeDirection(ConnectionPart next) {
			if (next != null) {
				return next.getComputedDirection().inverse();
			} else {
				return Direction.UNKNOWN;
			}
		}
		
		public Direction getComputedDirection() {
			if (coordinatesEqual(first.getX(), second.getX())) {
				return Direction.VERTICAL;
			} else 
			if (coordinatesEqual(first.getY(), second.getY())) {
				return Direction.HORIZONTAL;
			} else {
				return Direction.UNKNOWN;
			}
		}
		
		protected boolean coordinatesEqual(int a, int b) {
			return Math.abs(a - b) < 2;
		}
		
		public Point getReference(boolean start) {
			return start ? first : second;
		}
		
		public Point getRepairCandidate(boolean start) {
			return start ? second : first;
		}
		
		public boolean needsLayout() {
			return getComputedDirection() == Direction.UNKNOWN;
		}
	}
}