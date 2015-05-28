package thaumicenergistics.integration.tc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.aspect.AspectCache;
import thaumicenergistics.aspect.AspectStack;
import thaumicenergistics.fluids.GaseousEssentia;
import appeng.api.AEApi;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IItemList;

public final class EssentiaConversionHelper
{
	/**
	 * Singleton
	 */
	public static final EssentiaConversionHelper INSTANCE = new EssentiaConversionHelper();

	/**
	 * Private constructor
	 */
	private EssentiaConversionHelper()
	{

	}

	/**
	 * Converts an AE fluid stack into an AspectStack.
	 * 
	 * @param fluidStack
	 * @return Aspect stack if converted, null otherwise.
	 */
	public AspectStack convertAEFluidStackToAspectStack( final IAEFluidStack fluidStack )
	{
		// Is the fluid an essentia gas?
		if( fluidStack.getFluid() instanceof GaseousEssentia )
		{
			// Create an aspect stack to match the fluid
			return new AspectStack( ( (GaseousEssentia)fluidStack.getFluid() ).getAspect(), this.convertFluidAmountToEssentiaAmount( fluidStack
							.getStackSize() ) );
		}

		return null;
	}

	/**
	 * Converts an essentia amount into a fluid amount(mb).
	 * 
	 * @param essentiaAmount
	 * @return
	 */
	public long convertEssentiaAmountToFluidAmount( final long essentiaAmount )
	{
		return essentiaAmount * ThaumicEnergistics.config.conversionMultiplier();
	}

	/**
	 * Converts a fluid amount(mb) into an essentia amount.
	 * 
	 * @param fluidAmount
	 * @return
	 */
	public long convertFluidAmountToEssentiaAmount( final long fluidAmount )
	{
		return fluidAmount / ThaumicEnergistics.config.conversionMultiplier();
	}

	/**
	 * Converts an AE fluidstack list into a list of AspectStacks.
	 * 
	 * @param fluidStackList
	 * @return
	 * @deprecated Move to using the AspectCache
	 */
	@Deprecated
	public List<AspectStack> convertIAEFluidStackListToAspectStackList( final IItemList<IAEFluidStack> fluidStackList )
	{
		List<AspectStack> aspectStackList = new ArrayList<AspectStack>();

		if( fluidStackList != null )
		{
			for( IAEFluidStack fluidStack : fluidStackList )
			{
				// Convert
				AspectStack aspectStack = this.convertAEFluidStackToAspectStack( fluidStack );

				// Was the fluid an essentia gas?
				if( aspectStack != null )
				{
					// Add to the stack
					aspectStackList.add( aspectStack );
				}

			}
		}

		return aspectStackList;
	}

	/**
	 * Creates an AE fluidstack from the aspects in the specified contianer.
	 * 
	 * @param container
	 * @return Fluidstack if valid, null otherwise.
	 */
	public IAEFluidStack createAEFluidStackFromItemEssentiaContainer( final ItemStack container )
	{
		// Do we have an item?
		if( container == null )
		{
			return null;
		}

		// Is the item a container?
		if( !EssentiaItemContainerHelper.INSTANCE.isContainer( container ) )
		{
			return null;
		}

		// What aspect is in it?
		Aspect containerAspect = EssentiaItemContainerHelper.INSTANCE.getAspectInContainer( container );

		// Is there an aspect in it?
		if( containerAspect == null )
		{
			return null;
		}

		// Convert to gas
		GaseousEssentia essentiaGas = GaseousEssentia.getGasFromAspect( containerAspect );

		// Is there a fluid form of the aspect?
		if( essentiaGas == null )
		{
			return null;
		}

		// Get how much is in the container
		long containerAmount_EU = EssentiaItemContainerHelper.INSTANCE.getContainerStoredAmount( container );

		// Create and return the stack
		return this.createAEFluidStackInEssentiaUnits( essentiaGas, containerAmount_EU );

	}

	/**
	 * Creates an AE fluid stack from the specified essentia gas. This will
	 * convert the specified amount from essentia units to fluid units(mb).
	 * 
	 * @param Aspect
	 * @param essentiaAmount
	 * @return
	 */
	public IAEFluidStack createAEFluidStackInEssentiaUnits( final Aspect aspect, final long essentiaAmount )
	{
		GaseousEssentia essentiaGas = GaseousEssentia.getGasFromAspect( aspect );

		if( essentiaGas == null )
		{
			return null;
		}

		return this.createAEFluidStackInFluidUnits( essentiaGas, this.convertEssentiaAmountToFluidAmount( essentiaAmount ) );
	}

	/**
	 * Creates an AE fluid stack from the specified essentia gas. This will
	 * convert the specified amount from essentia units to fluid units(mb).
	 * 
	 * @param essentiaGas
	 * @param essentiaAmount
	 * @return
	 */
	public IAEFluidStack createAEFluidStackInEssentiaUnits( final GaseousEssentia essentiaGas, final long essentiaAmount )
	{
		return this.createAEFluidStackInFluidUnits( essentiaGas, this.convertEssentiaAmountToFluidAmount( essentiaAmount ) );
	}

	/**
	 * Creates an AE fluid stack from the specified essentia gas with the amount
	 * specified.
	 * 
	 * @param essentiaGas
	 * @param fluidAmount
	 * @return
	 */
	public IAEFluidStack createAEFluidStackInFluidUnits( final GaseousEssentia essentiaGas, final long fluidAmount )
	{
		IAEFluidStack ret = null;
		try
		{
			ret = AEApi.instance().storage().createFluidStack( new FluidStack( essentiaGas, 1 ) );

			ret.setStackSize( fluidAmount );
		}
		catch( Exception e )
		{
		}

		return ret;
	}

	/**
	 * Sets the values in the cache to reflect what is in the fluid stack list.
	 * If either the cache or list are null the method simply returns.
	 * 
	 * @param cache
	 * @param fluidStackList
	 * @return The passed AspectCache
	 */
	public AspectCache updateCacheToMatchIAEFluidStackList( final AspectCache cache, final IItemList<IAEFluidStack> fluidStackList )
	{
		// Validate cache and list
		if( ( cache == null ) || ( fluidStackList == null ) )
		{
			// Invalid
			return cache;
		}

		// Reset the cache
		cache.resetAll();

		// Loop over all fluids
		for( IAEFluidStack fluidStack : fluidStackList )
		{
			// Convert
			AspectStack aspectStack = this.convertAEFluidStackToAspectStack( fluidStack );

			// Was the fluid an essentia gas?
			if( aspectStack != null )
			{
				// Add to the cache
				cache.setAspect( aspectStack );
			}
		}

		return cache;
	}
}
