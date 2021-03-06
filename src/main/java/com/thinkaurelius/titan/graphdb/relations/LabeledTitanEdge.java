package com.thinkaurelius.titan.graphdb.relations;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.thinkaurelius.titan.core.*;
import com.thinkaurelius.titan.core.QueryException;
import com.thinkaurelius.titan.graphdb.adjacencylist.AdjacencyList;
import com.thinkaurelius.titan.graphdb.adjacencylist.AdjacencyListFactory;
import com.thinkaurelius.titan.graphdb.adjacencylist.ModificationStatus;
import com.thinkaurelius.titan.graphdb.blueprints.BlueprintsVertexUtil;
import com.thinkaurelius.titan.graphdb.query.AtomicQuery;
import com.thinkaurelius.titan.graphdb.query.SimpleTitanQuery;
import com.thinkaurelius.titan.graphdb.transaction.InternalTitanTransaction;
import com.thinkaurelius.titan.graphdb.vertices.InternalTitanVertex;
import com.thinkaurelius.titan.graphdb.vertices.VertexUtil;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

public class LabeledTitanEdge extends SimpleTitanEdge {

	protected volatile AdjacencyList outEdges;
    protected final ReentrantLock adjLock = new ReentrantLock();

	public LabeledTitanEdge(TitanLabel type, InternalTitanVertex start,
                            InternalTitanVertex end, InternalTitanTransaction tx, AdjacencyListFactory adjList) {
		super(type, start, end);
		assert !type.isSimple();
		assert tx!=null;
		this.tx=tx;
		outEdges = adjList.emptyList();
	}

	@Override
	public boolean addRelation(InternalRelation e, boolean isNew) {
		assert isAvailable();
		Preconditions.checkArgument(e.isIncidentOn(this), "Relation is not incident on this node!");
		Preconditions.checkArgument(e instanceof InlineRelation, "Expected inline edge!");
		Preconditions.checkArgument(e.isSimple() && e.isInline(),"Edge only supports simple, virtual edges!");
		Preconditions.checkArgument((e.isProperty()) || e.isUnidirected(),
				"Edge only supports properties or unidirected relationships");
		Preconditions.checkArgument(e.getVertex(0).equals(this),"This node only supports out edges!");

		ModificationStatus status = new ModificationStatus();
		adjLock.lock();
        try {
			outEdges = outEdges.addEdge(e, e.getType().isFunctional(),status);
		} finally {
            adjLock.unlock();
        }
		return status.hasChanged();

	}
	
	@Override
	public Iterable<InternalRelation> getRelations(AtomicQuery query,
                                                   boolean loadRemaining) {
		if (!query.isAllowedDirection(EdgeDirection.OUT)) return AdjacencyList.Empty;
		else return VertexUtil.filterByQuery(query, VertexUtil.getQuerySpecificIterable(outEdges, query));
	}
	
	@Override
	public void removeRelation(InternalRelation e) {
		assert isAccessible() && e.isIncidentOn(this) && e.getDirection(this)==Direction.OUT;
		outEdges.removeEdge(e,ModificationStatus.none);
	}

	@Override
	public void forceDelete() {
		super.forceDelete();
	}

	
	@Override
	public void loadedEdges(AtomicQuery query) {
		throw new UnsupportedOperationException("Relation loading is not supported on labeled edges");
	}

	@Override
	public boolean hasLoadedEdges(AtomicQuery query) {
		return true;
	}
	
	/* ---------------------------------------------------------------
	 * ###### The rest is copied verbatim from AbstractTitanVertex ##########
	 * ######### copied everything but "Changing Edges" and "In Memory TitanElement" section ######
	 * ---------------------------------------------------------------
	 */
	
	protected final InternalTitanTransaction tx;


    @Override
    public InternalTitanTransaction getTransaction() {
        return tx;
    }

    /* ---------------------------------------------------------------
      * In memory handling
      * ---------------------------------------------------------------
      */

    @Override
    public int hashCode() {
        if (hasID()) {
            return VertexUtil.getIDHashCode(this);
        } else {
            assert isNew();
            return super.hashCode();
        }

    }

    @Override
    public boolean equals(Object oth) {
        if (oth==this) return true;
        else if (!(oth instanceof InternalTitanVertex)) return false;
        InternalTitanVertex other = (InternalTitanVertex)oth;
        return VertexUtil.equalIDs(this, other);
    }

    @Override
    public InternalTitanVertex clone() throws CloneNotSupportedException{
        throw new CloneNotSupportedException();
    }

    /* ---------------------------------------------------------------
      * TitanRelation Iteration/Access
      * ---------------------------------------------------------------
      */

    @Override
    public TitanQuery query() {
        return tx.query(this);
    }

    @Override
    public Set<String> getPropertyKeys() {
        return BlueprintsVertexUtil.getPropertyKeys(this);
    }

    @Override
    public Object getProperty(TitanKey key) {
        Iterator<TitanProperty> iter = new SimpleTitanQuery(this).type(key).propertyIterator();
        if (!iter.hasNext()) return null;
        else {
            Object value = iter.next().getAttribute();
            if (iter.hasNext()) throw new QueryException("Multiple properties of specified type: " + key);
            return value;
        }
    }

    @Override
    public Object getProperty(String key) {
        if (!tx.containsType(key)) return null;
        else return getProperty(tx.getPropertyKey(key));
    }

    @Override
    public<O> O getProperty(TitanKey key, Class<O> clazz) {
        Iterator<TitanProperty> iter = new SimpleTitanQuery(this).type(key).propertyIterator();
        if (!iter.hasNext()) return null;
        else {
            O value = iter.next().getAttribute(clazz);
            if (iter.hasNext()) throw new QueryException("Multiple properties of specified type: " + key);
            return value;
        }
    }

    @Override
    public<O> O getProperty(String key, Class<O> clazz) {
        if (!tx.containsType(key)) return null;
        else return getProperty(tx.getPropertyKey(key), clazz);
    }

    @Override
    public Iterable<TitanProperty> getProperties() {
        return new SimpleTitanQuery(this).properties();
    }

    @Override
    public Iterable<TitanProperty> getProperties(TitanKey key) {
        return new SimpleTitanQuery(this).type(key).properties();
    }

    @Override
    public Iterable<TitanProperty> getProperties(String key) {
        return new SimpleTitanQuery(this).keys(key).properties();
    }



    @Override
    public Iterable<TitanEdge> getEdges() {
        return new SimpleTitanQuery(this).titanEdges();
    }


    @Override
    public Iterable<TitanEdge> getTitanEdges(Direction dir, TitanLabel... labels) {
        return new SimpleTitanQuery(this).direction(dir).types(labels).titanEdges();
    }

    @Override
    public Iterable<Edge> getEdges(Direction dir, String... labels) {
        return new SimpleTitanQuery(this).direction(dir).labels(labels).edges();
    }

    @Override
    public Iterable<TitanRelation> getRelations() {
        return new SimpleTitanQuery(this).relations();
    }

    @Override
    public Iterable<Vertex> getVertices(Direction direction, String... labels) {
        return new SimpleTitanQuery(this).direction(direction).labels(labels).vertices();
    }


    /* ---------------------------------------------------------------
      * TitanRelation Counts
      * ---------------------------------------------------------------
      */


    @Override
    public long getPropertyCount() {
        return new SimpleTitanQuery(this).propertyCount();
    }


    @Override
    public long getEdgeCount() {
        return new SimpleTitanQuery(this).count();
    }

    @Override
    public boolean isConnected() {
        return !Iterables.isEmpty(getEdges());
    }


    /* ---------------------------------------------------------------
      * Convenience Methods for TitanElement Creation
      * ---------------------------------------------------------------
      */

    @Override
    public TitanProperty addProperty(TitanKey key, Object attribute) {
        return tx.addProperty(this, key, attribute);
    }


    @Override
    public TitanProperty addProperty(String key, Object attribute) {
        return tx.addProperty(this, key, attribute);
    }

    @Override
    public void setProperty(String key, Object value) {
        BlueprintsVertexUtil.setProperty(this,tx,key,value);
    }

    @Override
    public Object removeProperty(String key) {
        return BlueprintsVertexUtil.removeProperty(this, tx, key);
    }


    @Override
    public TitanEdge addEdge(TitanLabel label, TitanVertex vertex) {
        return tx.addEdge(this, vertex, label);
    }


    @Override
    public TitanEdge addEdge(String label, TitanVertex vertex) {
        return tx.addEdge(this, vertex, label);
    }
}
