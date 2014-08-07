package thaumicenergistics.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import org.lwjgl.opengl.GL11;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.container.ContainerPartEssentiaIOBus;
import thaumicenergistics.gui.buttons.ButtonRedstoneModes;
import thaumicenergistics.gui.widget.WidgetAspectSlot;
import thaumicenergistics.network.IAspectSlotGui;
import thaumicenergistics.network.packet.server.PacketServerEssentiaIOBus;
import thaumicenergistics.parts.AEPartEssentiaIO;
import thaumicenergistics.texture.GuiTextureManager;
import thaumicenergistics.util.EssentiaItemContainerHelper;
import thaumicenergistics.util.GuiHelper;
import appeng.api.AEApi;
import appeng.api.config.RedstoneMode;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

// TODO: make redstone button -.-
@SideOnly(Side.CLIENT)
public class GuiEssentiatIO
	extends GuiWidgetHost
	implements WidgetAspectSlot.IConfigurable, IAspectSlotGui
{
	private AEPartEssentiaIO part;
	private EntityPlayer player;
	private byte filterSize;
	private List<WidgetAspectSlot> aspectSlotList = new ArrayList<WidgetAspectSlot>();
	private List<Aspect> filteredAspects = new ArrayList<Aspect>();
	private boolean redstoneControlled;
	private boolean hasNetworkTool;

	public GuiEssentiatIO( AEPartEssentiaIO terminal, EntityPlayer player )
	{
		super( new ContainerPartEssentiaIOBus( terminal, player ) );

		this.part = terminal;
		this.player = player;

		this.aspectSlotList.add( new WidgetAspectSlot( this, this.player, this.part, 0, 61, 21, this, (byte)2 ) );
		this.aspectSlotList.add( new WidgetAspectSlot( this, this.player, this.part, 1, 79, 21, this, (byte)1 ) );
		this.aspectSlotList.add( new WidgetAspectSlot( this, this.player, this.part, 2, 97, 21, this, (byte)2 ) );
		this.aspectSlotList.add( new WidgetAspectSlot( this, this.player, this.part, 3, 61, 39, this, (byte)1 ) );
		this.aspectSlotList.add( new WidgetAspectSlot( this, this.player, this.part, 4, 79, 39, this, (byte)0 ) );
		this.aspectSlotList.add( new WidgetAspectSlot( this, this.player, this.part, 5, 97, 39, this, (byte)1 ) );
		this.aspectSlotList.add( new WidgetAspectSlot( this, this.player, this.part, 6, 61, 57, this, (byte)2 ) );
		this.aspectSlotList.add( new WidgetAspectSlot( this, this.player, this.part, 7, 79, 57, this, (byte)1 ) );
		this.aspectSlotList.add( new WidgetAspectSlot( this, this.player, this.part, 8, 97, 57, this, (byte)2 ) );

		// Request a full update
		new PacketServerEssentiaIOBus().createRequestFullUpdate( this.player, this.part ).sendPacketToServer();

		this.hasNetworkTool = ( (ContainerPartEssentiaIOBus)this.inventorySlots ).hasNetworkTool();

		this.xSize = ( this.hasNetworkTool ? 246 : 211 );

		this.ySize = 184;
	}

	private boolean isMouseOverSlot( Slot slot, int x, int y )
	{
		return GuiHelper.isPointInGuiRegion( slot.xDisplayPosition, slot.yDisplayPosition, 16, 16, x, y, this.guiLeft, this.guiTop );
	}

	@Override
	protected void drawGuiContainerBackgroundLayer( float alpha, int mouseX, int mouseY )
	{	
		GL11.glColor4f( 1.0F, 1.0F, 1.0F, 1.0F );

		Minecraft.getMinecraft().renderEngine.bindTexture( GuiTextureManager.ESSENTIA_IO_BUS.getTexture() );

		this.drawTexturedModalRect( this.guiLeft, this.guiTop, 0, 0, 176, 184 );

		this.drawTexturedModalRect( this.guiLeft + 179, this.guiTop, 179, 0, 32, 86 );

		// Does the user have a network tool?
		if ( this.hasNetworkTool )
		{
			// Draw the tool gui
			this.drawTexturedModalRect( this.guiLeft + 179, this.guiTop + 93, 178, 93, 68, 68 );
		}
		
		// Call super
		super.drawGuiContainerBackgroundLayer( alpha, mouseX, mouseY );
	}

	protected Slot getSlotAtPosition( int x, int y )
	{
		for( int i = 0; i < this.inventorySlots.inventorySlots.size(); i++ )
		{
			Slot slot = (Slot)this.inventorySlots.inventorySlots.get( i );

			if ( this.isMouseOverSlot( slot, x, y ) )
			{
				return slot;
			}
		}

		return null;
	}

	@Override
	protected void mouseClicked( int mouseX, int mouseY, int mouseButton )
	{
		if ( this.hasNetworkTool )
		{
			Slot slot = this.getSlotAtPosition( mouseX, mouseY );

			if ( ( slot != null ) && ( slot.getStack() != null ) &&
							( slot.getStack().isItemEqual( AEApi.instance().items().itemNetworkTool.stack( 1 ) ) ) )
			{
				return;
			}
		}

		super.mouseClicked( mouseX, mouseY, mouseButton );

		for( WidgetAspectSlot aspectSlot : this.aspectSlotList )
		{
			if ( aspectSlot.isMouseOverWidget( mouseX, mouseY ) )
			{
				// Get the aspect of the currently held item
				Aspect itemAspect = EssentiaItemContainerHelper.getAspectInContainer( this.player.inventory.getItemStack() );

				// Is there an aspect?
				if ( itemAspect != null )
				{
					// Are we already filtering for this aspect?
					if ( this.filteredAspects.contains( itemAspect ) )
					{
						// Ignore
						return;
					}

				}

				aspectSlot.mouseClicked( itemAspect );

				break;
			}
		}
	}

	@Override
	public void actionPerformed( GuiButton button )
	{
		// Call super
		super.actionPerformed( button );

		// TODO: Check button ID == redstone button
		// new PacketServerEssentiaIO().createRequestChangeRedstoneMode( this.player, this.part );
	}

	@Override
	public void drawGuiContainerForegroundLayer( int mouseX, int mouseY )
	{
		super.drawGuiContainerForegroundLayer( mouseX, mouseY );

		boolean hoverUnderlayRendered = false;

		WidgetAspectSlot slotUnderMouse = null;

		for( int i = 0; i < 9; i++ )
		{
			WidgetAspectSlot slotWidget = this.aspectSlotList.get( i );

			if ( ( !hoverUnderlayRendered ) && ( slotWidget.canRender() ) && ( slotWidget.isMouseOverWidget( mouseX, mouseY ) ) )
			{
				slotWidget.drawMouseHoverUnderlay();
				
				slotUnderMouse = slotWidget;
				
				hoverUnderlayRendered = true;
			}

			slotWidget.drawWidget();
		}

		if ( slotUnderMouse != null )
		{
			slotUnderMouse.drawTooltip( mouseX - this.guiLeft, mouseY - this.guiTop );
		}
		else
		{
			for( Object button : this.buttonList )
			{
				if ( button instanceof ButtonRedstoneModes )
				{
					( (ButtonRedstoneModes)button ).drawTooltip( mouseX, mouseY );
				}
			}
		}
	}

	@Override
	public byte getConfigState()
	{
		return this.filterSize;
	}

	/**
	 * Called when the server sends if the bus is redstone controlled.
	 * @param redstoneControled
	 */
	public void onReceiveRedstoneControlled( boolean redstoneControled )
	{
		// Set redstone controlled
		this.redstoneControlled = redstoneControled;
	}

	/**
	 * Called when the server sends a filter size update.
	 * @param filterSize
	 */
	public void onReceiveFilterSize( byte filterSize )
	{	
		// Inform our part
		this.part.receiveFilterSize( filterSize );
		
		this.filterSize = filterSize;
	
		for( int i = 0; i < this.aspectSlotList.size(); i++ )
		{
			WidgetAspectSlot slot = this.aspectSlotList.get( i );
	
			if ( !slot.canRender() )
			{
				slot.setAspect( null );
			}
	
		}
	}

	public void onReceiveRedstoneMode( RedstoneMode redstoneMode )
	{
		// TODO: Fix this once a redstone button has been made
		if ( this.redstoneControlled && ( this.buttonList.size() > 0 ) )
		{
			( (ButtonRedstoneModes)this.buttonList.get( 0 ) ).setRedstoneMode( redstoneMode );
		}
	}

	@Override
	public void updateAspects( List<Aspect> aspectList )
	{
		// Inform our part
		this.part.receiveFilterList( aspectList );
		
		int count = Math.min( this.aspectSlotList.size(), aspectList.size() );

		for( int i = 0; i < count; i++ )
		{
			this.aspectSlotList.get( i ).setAspect( aspectList.get( i ) );
		}

		this.filteredAspects = aspectList;

	}

}
