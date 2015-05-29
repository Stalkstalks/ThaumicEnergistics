package thaumicenergistics.aspect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.fluids.GaseousEssentia;
import thaumicenergistics.integration.tc.EssentiaConversionHelper;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridStorage;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPostCacheConstruction;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IItemList;
import com.google.common.collect.ImmutableList;

/**
 * Cache of Aspects
 * 
 * @author Nividica
 * 
 */
public class GridEssentiaCache
	implements IMEEssentiaMonitor, IMEMonitorHandlerReceiver<IAEFluidStack>
{
	/**
	 * The amount of power required to transfer 1 essentia.
	 */
	public static final double AE_PER_ESSENTIA = 0.3;

	/**
	 * The actual cache of aspects.
	 */
	private final Hashtable<Aspect, AspectStack> cache;

	/**
	 * Grid the cache is part of.
	 */
	private final IGrid internalGrid;

	/**
	 * Objects who wish to be notified of any changes.
	 */
	private final HashMap<IMEEssentiaMonitorReceiver, Object> listeners;

	/**
	 * The grids fluid monitor.
	 */
	private IMEMonitor<IAEFluidStack> fluidMonitor;

	/**
	 * The grids energy manager.
	 */
	private IEnergyGrid energyGrid;

	/**
	 * Collection backed by the cache that shows the essentia amounts in the network. Is created the first time it is needed.<br>
	 * Any changes to the cache are reflected in the view.
	 */
	private Collection<AspectStack> cacheView;

	/**
	 * When true the full storage list needs to be pulled to update the cache.
	 */
	private boolean cacheNeedsUpdate = false;

	public GridEssentiaCache( final IGrid grid )
	{
		// Create the cache
		this.cache = new Hashtable<Aspect, AspectStack>();

		// Set the grid
		this.internalGrid = grid;

		// Create the listeners table
		this.listeners = new HashMap<IMEEssentiaMonitorReceiver, Object>();

	}

	/**
	 * Changes the amount in the cache by the specified difference amount.<br>
	 * If {@code aspect} is null, no changes will be made. Returns 0.<br>
	 * If {@code diff} is positive and {@code aspect} stored, the resulting amount will be stored. Returns amount added.<br>
	 * If {@code diff} is positive and {@code aspect} is not stored, {@code aspect} will be stored with the value of {@code diff}. Returns
	 * {@code diff}<br>
	 * If {@code diff} is negative and {@code aspect} is stored, {@code aspect} will be removed if the resulting amount is <= 0. Returns amount
	 * removed.<br>
	 * If {@code diff} is negative and {@code aspect} is not stored, no changes will be made. Returns 0<br>
	 * 
	 * @param aspect
	 * @param diff
	 * @return Amount removed or added.
	 */
	private long changeAspectAmount( final Aspect aspect, final long diff )
	{
		// Is the aspect null?
		if( aspect == null )
		{
			// No changes
			return 0;
		}

		// Get the current amount
		AspectStack prevStack = this.cache.get( aspect );

		if( prevStack == null )
		{
			// If the current amount is null the diff must be positive
			if( diff > 0 )
			{
				// Add the aspect
				this.cache.put( aspect, new AspectStack( aspect, diff ) );
				return diff;
			}

			// No changes
			return 0;
		}

		// Calculate the new amount
		long prevAmount = prevStack.stackSize;
		long newAmount = Math.max( 0L, prevAmount + diff );

		// Is there any left?
		if( newAmount > 0 )
		{
			// Update the amount
			prevStack.stackSize = newAmount;
		}
		else
		{
			// Remove the aspect
			this.cache.remove( aspect );
		}

		return newAmount - prevAmount;

	}

	/**
	 * Mirror method of the injectEssentia. Used to defer power calculations, because the simulation is not always accurate, and Essentia gas should
	 * not be stored in partial amounts.
	 * 
	 * @param aspect
	 * @param amount
	 * @param mode
	 * @param source
	 * @return Amount that was <strong>not</strong> injected
	 */
	private long injectEssentiaSafely( final Aspect aspect, final long amount, final Actionable mode, final BaseActionSource source,
										final GaseousEssentia essentiaGas )
	{
		// Create the fluid request
		IAEFluidStack fluidRequest = EssentiaConversionHelper.INSTANCE.createAEFluidStackInEssentiaUnits( essentiaGas, amount );

		// Inject fluid
		IAEFluidStack fluidRejected = this.fluidMonitor.injectItems( fluidRequest, mode, source );

		// Was any rejected?
		if( ( fluidRejected != null ) && ( fluidRejected.getStackSize() > 0 ) )
		{
			if( fluidRejected.getStackSize() == fluidRequest.getStackSize() )
			{
				// All essentia was rejected
				return amount;
			}

			// Calculate the adjusted amount, essentia gas can not be stored in partial units
			long rejectedAdjustedAmount = EssentiaConversionHelper.INSTANCE.convertFluidAmountToEssentiaAmount( fluidRejected.getStackSize() );
			return rejectedAdjustedAmount;
		}

		// All essentia was accepted.
		return 0;
	}

	/**
	 * Notifies all listeners of the specified changes.
	 * 
	 * @param changes
	 */
	private void notifyListeners( final List<AspectStack> changes )
	{
		// Get an immutable copy
		ImmutableList<AspectStack> changeList = ImmutableList.copyOf( changes );

		// Get the listener iterator
		Iterator<Entry<IMEEssentiaMonitorReceiver, Object>> entryIterator = this.listeners.entrySet().iterator();

		// Inform all listeners of the changes
		while( entryIterator.hasNext() )
		{
			// Get the listener
			Entry<IMEEssentiaMonitorReceiver, Object> entry = entryIterator.next();

			// Validate the token
			if( entry.getKey().isValid( entry.getValue() ) )
			{
				// Valid token
				entry.getKey().postChange( changeList );
			}
			else
			{
				// Invalid token, remove from listener list
				entryIterator.remove();
			}
		}
	}

	/**
	 * Updates the cache to match the contents of the network and updates any
	 * listeners of the changes.
	 */
	@SuppressWarnings("null")
	private void updateCacheToMatchNetwork()
	{
		synchronized( this.cache )
		{
			// Get the list of fluids in the network
			IItemList<IAEFluidStack> fluidStackList;

			// Validate the list
			if( ( fluidStackList = this.fluidMonitor.getStorageList() ) == null )
			{
				// Invalid list
				return;
			}

			// Changes made to the cache
			List<AspectStack> aspectChanges = null;

			// The currently stored aspects
			Set<Aspect> previousAspects = null;

			// Are there any listeners?
			boolean hasListeners = ( this.listeners.size() > 0 );
			if( hasListeners )
			{
				// Create the change trackers
				aspectChanges = new ArrayList<AspectStack>();
				previousAspects = new HashSet<Aspect>( this.cache.keySet() );
			}
			else
			{
				// Can safely clear the cache
				this.cache.clear();
			}

			// Loop over all fluids
			for( IAEFluidStack fluidStack : fluidStackList )
			{
				// Ensure the fluid is an essentia gas
				if( !( fluidStack.getFluid() instanceof GaseousEssentia ) )
				{
					// Not an essentia gas.
					continue;
				}

				// Get the gas aspect
				Aspect aspect = ( (GaseousEssentia)fluidStack.getFluid() ).getAspect();

				// Calculate the new amount
				Long newAmount = EssentiaConversionHelper.INSTANCE.convertFluidAmountToEssentiaAmount( fluidStack.getStackSize() );

				// Update the cache
				AspectStack prevStack = this.cache.put( aspect, new AspectStack( aspect, newAmount ) );

				// Are there any listeners?
				if( hasListeners )
				{
					// Calculate the difference
					long diff = ( newAmount - ( prevStack != null ? prevStack.stackSize : 0 ) );

					if( diff != 0 )
					{
						// Add to the changes
						aspectChanges.add( new AspectStack( aspect, diff ) );

						// Remove from the previous mapping
						previousAspects.remove( aspect );
					}
				}
			}

			// Are there any listeners?
			if( hasListeners )
			{
				// Anything left in the previous mapping is no longer present in the network
				for( Aspect aspect : previousAspects )
				{
					aspectChanges.add( new AspectStack( aspect, -this.cache.remove( aspect ).stackSize ) );
				}

				// Notify listeners
				this.notifyListeners( aspectChanges );
			}

			// Mark the cache as valid
			this.cacheNeedsUpdate = false;
		}
	}

	@Override
	public void addListener( final IMEEssentiaMonitorReceiver listener, final Object verificationToken )
	{
		// If this is the first listener, and the cache is out of sync, it needs to be updated first
		if( ( this.listeners.size() == 0 ) && ( this.cacheNeedsUpdate ) )
		{
			this.updateCacheToMatchNetwork();
		}

		this.listeners.put( listener, verificationToken );
	}

	@Override
	public void addNode( final IGridNode gridNode, final IGridHost machine )
	{
		// Ignored
	}

	@Override
	public long extractEssentia( final Aspect aspect, final long amount, final Actionable mode, final BaseActionSource source )
	{
		// Ensure the aspect is not null, and the amount is > 0 
		if( ( aspect == null ) || ( amount <= 0 ) )
		{
			// Invalid arguments
			return 0;
		}

		// Get the gas form of the essentia
		GaseousEssentia essentiaGas;

		// Ensure there is a gas form of the aspect.
		if( ( essentiaGas = GaseousEssentia.getGasFromAspect( aspect ) ) == null )
		{
			// Unregistered aspect :(
			return 0;
		}

		// Simulate power extraction
		double powerRequest = GridEssentiaCache.AE_PER_ESSENTIA * amount;
		double powerReceived = this.energyGrid.extractAEPower( powerRequest, Actionable.SIMULATE, PowerMultiplier.CONFIG );

		// Was enough power extracted?
		if( powerReceived < powerRequest )
		{
			// Not enough power
			return 0;
		}

		// Create the fluid request
		IAEFluidStack fluidRequest = EssentiaConversionHelper.INSTANCE.createAEFluidStackInEssentiaUnits( essentiaGas, amount );

		// Attempt the extraction
		IAEFluidStack fluidReceived = this.fluidMonitor.extractItems( fluidRequest, mode, source );

		// Was any fluid received?
		if( ( fluidReceived == null ) || ( fluidReceived.getStackSize() <= 0 ) )
		{
			// Fluid not found.
			return 0;
		}

		// Extract power if modulating
		if( mode == Actionable.MODULATE )
		{
			this.energyGrid.extractAEPower( powerRequest, Actionable.MODULATE, PowerMultiplier.CONFIG );
		}

		// Convert the received fluid into an aspect stack
		return EssentiaConversionHelper.INSTANCE.convertFluidAmountToEssentiaAmount( fluidReceived.getStackSize() );
	}

	@Override
	public long getEssentiaAmount( final Aspect aspect )
	{
		// Does the cache need to be updated?
		if( this.cacheNeedsUpdate )
		{
			// Update the cache
			this.updateCacheToMatchNetwork();
		}

		// Does the cache have this key?
		if( this.cache.containsKey( aspect ) )
		{
			// Return the amount
			return this.cache.get( aspect ).stackSize;
		}

		// Invalid aspect
		return 0;
	}

	@Override
	public Collection<AspectStack> getEssentiaList()
	{
		// Does the cache need to be updated?
		if( this.cacheNeedsUpdate )
		{
			this.updateCacheToMatchNetwork();
		}

		// Does the view need to be created?
		if( this.cacheView == null )
		{
			this.cacheView = Collections.unmodifiableCollection( this.cache.values() );
		}

		return this.cacheView;
	}

	@Override
	public long injectEssentia( final Aspect aspect, final long amount, final Actionable mode, final BaseActionSource source )
	{
		// Ensure the aspect is not null, and the amount is > 0 
		if( ( aspect == null ) || ( amount <= 0 ) )
		{
			// Invalid arguments
			return amount;
		}

		// Get the gas form of the essentia
		GaseousEssentia essentiaGas;

		// Ensure there is a gas form of the aspect.
		if( ( essentiaGas = GaseousEssentia.getGasFromAspect( aspect ) ) == null )
		{
			// Unregistered aspect :(
			return amount;
		}

		// Simulate the injection
		long rejectedAmount = this.injectEssentiaSafely( aspect, amount, Actionable.SIMULATE, source, essentiaGas );
		long adjustedAmount = amount - rejectedAmount;

		// Simulate power extraction
		double powerRequest = GridEssentiaCache.AE_PER_ESSENTIA * adjustedAmount;
		double powerReceived = this.energyGrid.extractAEPower( powerRequest, Actionable.SIMULATE, PowerMultiplier.CONFIG );

		// Was enough power extracted?
		if( powerReceived < powerRequest )
		{
			// Not enough power
			return amount;
		}

		// Modulating?
		if( mode == Actionable.MODULATE )
		{
			// Inject
			rejectedAmount = this.injectEssentiaSafely( aspect, adjustedAmount, Actionable.MODULATE, source, essentiaGas );
			adjustedAmount -= rejectedAmount;

			// Adjust and extract power
			powerRequest = GridEssentiaCache.AE_PER_ESSENTIA * adjustedAmount;
			this.energyGrid.extractAEPower( powerRequest, Actionable.MODULATE, PowerMultiplier.CONFIG );
		}

		return rejectedAmount;
	}

	@Override
	public boolean isValid( final Object verificationToken )
	{
		return this.internalGrid.equals( verificationToken );
	}

	@MENetworkEventSubscribe
	public void onGridCacheReady( final MENetworkPostCacheConstruction event )
	{
		// Get the storage grid
		IStorageGrid storage = (IStorageGrid)this.internalGrid.getCache( IStorageGrid.class );

		// Get the energy grid
		this.energyGrid = (IEnergyGrid)this.internalGrid.getCache( IEnergyGrid.class );

		// Get the fluid monitor
		this.fluidMonitor = storage.getFluidInventory();

		// Add listener
		this.fluidMonitor.addListener( this, this.internalGrid );

		// Mark that the cache needs to be updated
		this.cacheNeedsUpdate = true;

	}

	@Override
	public void onJoin( final IGridStorage sourceStorage )
	{
		// Mark that the cache needs to be updated
		this.cacheNeedsUpdate = true;

	}

	@Override
	public void onListUpdate()
	{
		// Mark that the cache needs to be updated
		this.cacheNeedsUpdate = true;
	}

	@Override
	public void onSplit( final IGridStorage destinationStorage )
	{
	}

	@Override
	public void onUpdateTick()
	{
		try
		{
			/*
			 * If the cache is invalid and there are listeners this will update the cache to match the network.
			 * If there are no listeners the update is deferred until there are listeners, or the cache is accessed.
			 */
			if( this.cacheNeedsUpdate && ( this.listeners.size() > 0 ) )
			{
				// Update the cache
				this.updateCacheToMatchNetwork();
			}
		}
		catch( Exception e )
		{
			// Ignored
		}
	}

	@Override
	public void populateGridStorage( final IGridStorage destinationStorage )
	{
		// Ignored
	}

	@Override
	public void postChange( final IBaseMonitor<IAEFluidStack> monitor, final Iterable<IAEFluidStack> fluidChanges, final BaseActionSource actionSource )
	{
		// Ensure the cache is up to date
		if( this.cacheNeedsUpdate )
		{
			// No use updating an out of sync cache
			return;

			/*
			 * Note: this should never happen if there are listeners. As the cache will be updated when a list update occurs.
			 * If any changes occur between a call to onListUpdate() and onUpdateTick(), those changes will be ignored until the cache is updated. 
			 */
		}

		// Ensure there was a change
		if( fluidChanges == null )
		{
			return;
		}

		// True if there are any listeners
		boolean hasListeners = ( this.listeners.size() > 0 );

		// Changes made to the cache.
		List<AspectStack> aspectChanges = null;

		// Search the changes for essentia gas
		for( IAEFluidStack change : fluidChanges )
		{
			// Is the change an essentia gas?
			if( ( change.getFluid() instanceof GaseousEssentia ) )
			{
				// Get the aspect
				Aspect aspect = ( (GaseousEssentia)change.getFluid() ).getAspect();

				// Calculate the difference
				long diff = EssentiaConversionHelper.INSTANCE.convertFluidAmountToEssentiaAmount( change.getStackSize() );

				// Update the cache
				diff = this.changeAspectAmount( aspect, diff );

				// Add to the changes
				if( hasListeners )
				{
					// Create the change list if needed
					if( aspectChanges == null )
					{
						aspectChanges = new ArrayList<AspectStack>();
					}

					// Add the change
					aspectChanges.add( new AspectStack( aspect, diff ) );
				}
			}
		}

		// Notify any listeners
		if( ( aspectChanges != null ) && ( aspectChanges.size() > 0 ) )
		{
			this.notifyListeners( aspectChanges );
		}

	}

	@Override
	public void removeListener( final IMEEssentiaMonitorReceiver listener )
	{
		this.listeners.remove( listener );
	}

	@Override
	public void removeNode( final IGridNode gridNode, final IGridHost machine )
	{
		// Ignored
	}

}
