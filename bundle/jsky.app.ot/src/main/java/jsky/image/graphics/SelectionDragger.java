package jsky.image.graphics;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

import diva.canvas.Figure;
import diva.canvas.FigureDecorator;
import diva.canvas.FigureLayer;
import diva.canvas.GeometricSet;
import diva.canvas.GraphicsPane;
import diva.canvas.OverlayLayer;
import diva.canvas.event.EventLayer;
import diva.canvas.event.LayerEvent;
import diva.canvas.event.MouseFilter;
import diva.canvas.interactor.DragInteractor;
import diva.canvas.interactor.Interactor;
import diva.canvas.interactor.SelectionInteractor;
import diva.util.CompoundIterator;

/** A class that implements rubber-banding on a canvas. It contains
 * references to one or more instances of SelectionInteractor, which it
 * notifies whenever dragging on the canvas covers or uncovers items.
 * The SelectionDragger requires three layers: an Event Layer,
 * which it listens to perform drag-selection, an OutlineLayer, on
 * which it draws the drag-selection box, and a FigureLayer, which it
 * selects figures on. It can also accept a GraphicsPane in its
 * constructor, in which case it will use the background event layer,
 * outline layer, and foreground event layer from that pane.
 *
 * @version $Revision: 4414 $
 * @author John Reekie
 */
public class SelectionDragger extends DragInteractor {

    /* The overlay layer
     */
    private OverlayLayer _overlayLayer;

    /* The event layer
     */
    private EventLayer _eventLayer;

    /* The figure layer
     */
    private FigureLayer _figureLayer;

    /* The set of valid selection interactors
     */
    private final ArrayList<SelectionInteractor> _selectionInteractors = new ArrayList<>();

    /* The rubber-band
     */
    private Rectangle2D _rubberBand = null;

    /* The set of figures covered by the rubber-band
     */
    private GeometricSet _intersectedFigures;

    /** A hash-set containing those figures
     */
    private HashSet<Figure> _currentFigures;

    /** A hash-set containing figures that overlap the rubber-band
     * but are not "hit"
     */
    private HashSet<Figure> _holdovers = new HashSet<>();

    /* The origin points
     */
    private double _originX;
    private double _originY;

    /** The mouse filter for selecting items
     */
    private MouseFilter _selectionFilter = MouseFilter.selectionFilter;

    /** The mouse filter for toggling items
     */
    private MouseFilter _toggleFilter = MouseFilter.alternateSelectionFilter;

    /** The selection mode flags
     */
    private boolean _isSelecting;
    private boolean _isToggling;

    /**
     * Create a new SelectionDragger attached to the given graphics
     * pane.
     */
    public SelectionDragger(GraphicsPane gpane) {
        super();
        setOverlayLayer(gpane.getOverlayLayer());
        setEventLayer(gpane.getBackgroundEventLayer());
        setFigureLayer(gpane.getForegroundLayer());
    }

    ///////////////////////////////////////////////////////////////////
    //// public methods

    /**
     * Add a selection interactor to the list of valid interactor.
     * When drag-selecting, only figures that have an interactor
     * in this last are added to the selection model.
     */
    public void addSelectionInteractor(SelectionInteractor i) {
        if (!(_selectionInteractors.contains(i))) {
            _selectionInteractors.add(i);
        }
    }

    /**
     * Clear the selection in all the relevant selection interactors.
     */
    public void clearSelection() {
        for (SelectionInteractor i : _selectionInteractors) {
            i.getSelectionModel().clearSelection();
        }
    }

    /**
     * Contract the selection by removing an item from it and
     * removing highlight rendering. If the figure is not in
     * the selection, do nothing.
     */
    public void contractSelection(SelectionInteractor i, Figure figure) {
        if (i.getSelectionModel().containsSelection(figure)) {
            i.getSelectionModel().removeSelection(figure);
        }
    }

    /**
     * Expand the selection by adding an item to it and adding
     * highlight rendering to it. If the
     * figure is already in the selection, do nothing.
     */
    public void expandSelection(SelectionInteractor i, Figure figure) {
        if (!(i.getSelectionModel().containsSelection(figure))) {
            i.getSelectionModel().addSelection(figure);
        }
    }

    /** Reshape the rubber-band, swapping coordinates if necessary.
     * Any figures that are newly included or excluded from
     * the drag region are added to or removed from the appropriate
     * selection.
     */
    @SuppressWarnings("unchecked")
    public void mouseDragged(LayerEvent event) {
        if (!isEnabled()) {
            return;
        }
        if (!_isToggling && !_isSelecting) {
            return;
        }

        double x = event.getLayerX();
        double y = event.getLayerY();
        double w;
        double h;

        // Figure out the coordinates of the rubber band
        _overlayLayer.repaint(_rubberBand);
        if (x < _originX) {
            w = _originX - x;
        } else {
            w = x - _originX;
            x = _originX;
        }
        if (y < _originY) {
            h = _originY - y;
        } else {
            h = y - _originY;
            y = _originY;
        }
        _rubberBand.setFrame(x, y, w, h);
        _overlayLayer.repaint(_rubberBand);

        // Update the intersected figure set
        _intersectedFigures.setGeometry(_rubberBand);
        HashSet<Figure> freshFigures = new HashSet<>();
        for (Iterator<Figure> i = _intersectedFigures.figures(); i.hasNext();) {
            Figure f = i.next();
            if (f instanceof FigureDecorator) {
                f = ((FigureDecorator) f).getDecoratedFigure();
            }
            if (f.hit(_rubberBand)) {
                freshFigures.add(f);
            } else {
                _holdovers.add(f);
            }
        }
        for (Object o : ((HashSet) _holdovers.clone())) {
            Figure f = (Figure) o;
            if (f.hit(_rubberBand)) {
                freshFigures.add(f);
                _holdovers.remove(f);
            }
        }
        HashSet<Figure> staleFigures = (HashSet<Figure>)_currentFigures.clone();
        staleFigures.removeAll(freshFigures);
        HashSet temp = (HashSet) freshFigures.clone();
        freshFigures.removeAll(_currentFigures);
        _currentFigures = temp;

        // If in selection mode, add and remove figures
        if (_isSelecting) {
            // Add figures to the selection
            for (Figure f: freshFigures) {
                Interactor r = f.getInteractor();
                if (r != null &&
                        r instanceof SelectionInteractor &&
                        _selectionInteractors.contains(r)) {
                    expandSelection((SelectionInteractor) r, f);
                }
            }

            // Remove figures from the selection
            for (Figure f: staleFigures) {
                Interactor r = f.getInteractor();
                if (r != null &&
                        r instanceof SelectionInteractor &&
                        _selectionInteractors.contains(r)) {
                    contractSelection((SelectionInteractor) r, f);
                }
            }
        } else {
            // Toggle figures into and out of the selection
            Iterator i = new CompoundIterator(
                    freshFigures.iterator(),
                    staleFigures.iterator());
            while (i.hasNext()) {
                Figure f = (Figure) i.next();
                Interactor r = f.getInteractor();
                if (r != null &&
                        r instanceof SelectionInteractor &&
                        _selectionInteractors.contains(r)) {
                    SelectionInteractor s = (SelectionInteractor) r;
                    if (s.getSelectionModel().containsSelection(f)) {
                        contractSelection(s, f);
                    } else {
                        expandSelection(s, f);
                    }
                }
            }
        }
        // Consume the event
        if (isConsuming()) {
            event.consume();
        }
    }

    /** Clear the selection, and create the rubber-band
     */
    public void mousePressed(LayerEvent event) {
        if (!isEnabled()) {
            return;
        }
        // Check mouse event, set flags, etc
        _isSelecting = _selectionFilter.accept(event);
        _isToggling = _toggleFilter.accept(event);

        if (!_isToggling && !_isSelecting) {
            return;
        }

        // Do it
        _originX = event.getLayerX();
        _originY = event.getLayerY();
        _rubberBand = new Rectangle2D.Double(
                _originX,
                _originY,
                0.0,
                0.0);

        _overlayLayer.add(_rubberBand);
        _overlayLayer.repaint(_rubberBand);

        _intersectedFigures =
                _figureLayer.getFigures().getIntersectedFigures(_rubberBand);
        _currentFigures = new HashSet<>();
        _holdovers = new HashSet<>();

        // Clear all selections
        if (_isSelecting) {
            clearSelection();
        }
        // Consume the event
        if (isConsuming()) {
            event.consume();
        }
    }

    /** Delete the rubber-band
     */
    public void mouseReleased(LayerEvent event) {
        if (!isEnabled()) {
            return;
        }
        terminateDragSelection();

        // Consume the event
        if (isConsuming()) {
            event.consume();
        }
    }

    /**
     * Set the layer that drag rectangles are drawn on
     */
    public void setOverlayLayer(OverlayLayer l) {
        _overlayLayer = l;
    }

    /**
     * Set the layer that drag events are listened on
     */
    public void setEventLayer(EventLayer l) {
        if (_eventLayer != null) {
            _eventLayer.removeLayerListener(this);
        }
        _eventLayer = l;
        _eventLayer.addLayerListener(this);
    }

    /**
     * Set the layer that figures are selected on
     */
    public void setFigureLayer(FigureLayer l) {
        _figureLayer = l;
    }

    /** Terminate drag-selection operation. This must only be called
     * from events that are triggered during a drag operation.
     */
    public void terminateDragSelection() {
        if (!_isToggling && !_isSelecting) {
            return;
        }

        _overlayLayer.repaint(_rubberBand);
        _overlayLayer.remove(_rubberBand);
        //_rubberBand = null;
        _currentFigures = null;
        _holdovers = null;
    }

    // -- allan: added these --

    /* Return the rectangle of the selected area.
     */
    public Rectangle2D getSelectedArea() {
        return _rubberBand;
    }

    /** Enable/disable drag-selecting */
    public void setEnabled(final boolean enabled) {
        if (_eventLayer != null) {
            _eventLayer.removeLayerListener(this);
            if (enabled)
                _eventLayer.addLayerListener(this);
        }
    }
}
