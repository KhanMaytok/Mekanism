package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.api.EnumColor;
import mekanism.api.IConfigurable;
import mekanism.api.MekanismConfig.usage;
import mekanism.common.base.ISustainedTank;
import mekanism.common.block.BlockMachine.MachineType;
import mekanism.common.integration.IComputerIntegration;
import mekanism.common.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public class TileEntityFluidicPlenisher extends TileEntityElectricBlock implements IComputerIntegration, IConfigurable, IFluidHandler, ISustainedTank
{
	public Set<Coord4D> activeNodes = new HashSet<Coord4D>();
	public Set<Coord4D> usedNodes = new HashSet<Coord4D>();
	
	public boolean finishedCalc = false;
	
	public FluidTank fluidTank = new FluidTank(10000);
	
	private static EnumSet<EnumFacing> dirs = EnumSet.complementOf(EnumSet.of(EnumFacing.UP));
	private static int MAX_NODES = 4000;
	
	public TileEntityFluidicPlenisher()
	{
		super("FluidicPlenisher", MachineType.FLUIDIC_PLENISHER.baseEnergy);
		inventory = new ItemStack[3];
	}
	
	@Override
	public void onUpdate()
	{
		if(!worldObj.isRemote)
		{
			ChargeUtils.discharge(2, this);
			
			if(inventory[0] != null)
			{
				if(inventory[0].getItem() instanceof IFluidContainerItem && ((IFluidContainerItem)inventory[0].getItem()).getFluid(inventory[0]) != null)
				{
					if(((IFluidContainerItem)inventory[0].getItem()).getFluid(inventory[0]).getFluid().canBePlacedInWorld())
					{
						fluidTank.fill(FluidContainerUtils.extractFluid(fluidTank, inventory[0]), true);

						if(((IFluidContainerItem) inventory[0].getItem()).getFluid(inventory[0]) == null || fluidTank.getFluidAmount() == fluidTank.getCapacity())
						{
							if(inventory[1] == null)
							{
								inventory[1] = inventory[0].copy();
								inventory[0] = null;

								markDirty();
							}
						}
					}
				}
				else if(FluidContainerRegistry.isFilledContainer(inventory[0]))
				{
					FluidStack itemFluid = FluidContainerRegistry.getFluidForFilledItem(inventory[0]);
	
					if((fluidTank.getFluid() == null && itemFluid.amount <= fluidTank.getCapacity()) || fluidTank.getFluid().amount+itemFluid.amount <= fluidTank.getCapacity())
					{
						if((fluidTank.getFluid() != null && !fluidTank.getFluid().isFluidEqual(itemFluid)) || !itemFluid.getFluid().canBePlacedInWorld())
						{
							return;
						}
	
						ItemStack containerItem = inventory[0].getItem().getContainerItem(inventory[0]);
	
						boolean filled = false;
	
						if(containerItem != null)
						{
							if(inventory[1] == null || (inventory[1].isItemEqual(containerItem) && inventory[1].stackSize+1 <= containerItem.getMaxStackSize()))
							{
								inventory[0] = null;
	
								if(inventory[1] == null)
								{
									inventory[1] = containerItem;
								}
								else {
									inventory[1].stackSize++;
								}
	
								filled = true;
							}
						}
						else {
							inventory[0].stackSize--;
	
							if(inventory[0].stackSize == 0)
							{
								inventory[0] = null;
							}
	
							filled = true;
						}
	
						if(filled)
						{
							fluidTank.fill(itemFluid, true);
							markDirty();
						}
					}
				}
			}
			
			if(getEnergy() >= usage.fluidicPlenisherUsage && worldObj.getWorldTime() % 10 == 0 && fluidTank.getFluidAmount() >= FluidContainerRegistry.BUCKET_VOLUME)
			{
				if(fluidTank.getFluid().getFluid().canBePlacedInWorld())
				{
					if(!finishedCalc)
					{
						doPlenish();
					}
					else {
						Coord4D below = Coord4D.get(this).offset(EnumFacing.DOWN);
						
						if(canReplace(below, false, false) && getEnergy() >= usage.fluidicPlenisherUsage && fluidTank.getFluidAmount() >= FluidContainerRegistry.BUCKET_VOLUME)
						{
							if(fluidTank.getFluid().getFluid().canBePlacedInWorld())
							{
								worldObj.setBlockState(below, MekanismUtils.getFlowingBlock(fluidTank.getFluid().getFluid()).getDefaultState(), 3);
								
								setEnergy(getEnergy() - usage.fluidicPlenisherUsage);
								fluidTank.drain(FluidContainerRegistry.BUCKET_VOLUME, true);
							}
						}
					}
				}
			}
		}
	}
	
	private void doPlenish()
	{
		if(usedNodes.size() >= MAX_NODES)
		{
			finishedCalc = true;
			return;
		}
		
		if(activeNodes.isEmpty())
		{
			if(usedNodes.isEmpty())
			{
				Coord4D below = Coord4D.get(this).offset(EnumFacing.DOWN);
				
				if(!canReplace(below, true, true))
				{
					finishedCalc = true;
					return;
				}

				activeNodes.add(below);
			}
			else {
				finishedCalc = true;
				return;
			}
		}
		
		Set<Coord4D> toRemove = new HashSet<Coord4D>();
		
		for(Coord4D coord : activeNodes)
		{
			if(coord.exists(worldObj))
			{
				if(canReplace(coord, true, false))
				{
					worldObj.setBlockState(coord, MekanismUtils.getFlowingBlock(fluidTank.getFluid().getFluid()).getDefaultState(), 3);

					setEnergy(getEnergy() - usage.fluidicPlenisherUsage);
					fluidTank.drain(FluidContainerRegistry.BUCKET_VOLUME, true);

				}
				
				for(EnumFacing dir : dirs)
				{
					Coord4D sideCoord = coord.offset(dir);
					
					if(sideCoord.exists(worldObj) && canReplace(sideCoord, true, true))
					{
						activeNodes.add(sideCoord);
					}
				}
				
				toRemove.add(coord);
				break;
			}
			else {
				toRemove.add(coord);
			}
		}
		
		for(Coord4D coord : toRemove)
		{
			activeNodes.remove(coord);
			usedNodes.add(coord);
		}
	}
	
	public int getActiveY()
	{
		return getPos().down().getY();
	}
	
	public boolean canReplace(Coord4D coord, boolean checkNodes, boolean isPathfinding)
	{
		if(checkNodes && usedNodes.contains(coord))
		{
			return false;
		}
		
		if(coord.isAirBlock(worldObj) || MekanismUtils.isDeadFluid(worldObj, coord))
		{
			return true;
		}
		
		if(MekanismUtils.isFluid(worldObj, coord))
		{
			return isPathfinding;
		}
		
		return coord.getBlock(worldObj).isReplaceable(worldObj, coord);
	}
	
	@Override
	public void handlePacketData(ByteBuf dataStream)
	{
		super.handlePacketData(dataStream);
		
		finishedCalc = dataStream.readBoolean();

		if(dataStream.readInt() == 1)
		{
			fluidTank.setFluid(new FluidStack(FluidRegistry.getFluid(ByteBufUtils.readUTF8String(dataStream)), dataStream.readInt()));
		}
		else {
			fluidTank.setFluid(null);
		}

		MekanismUtils.updateBlock(worldObj, getPos());
	}

	@Override
	public ArrayList getNetworkedData(ArrayList<Object> data)
	{
		super.getNetworkedData(data);
		
		data.add(finishedCalc);

		if(fluidTank.getFluid() != null)
		{
			data.add(1);
			data.add(fluidTank.getFluid().getFluid().getName());
			data.add(fluidTank.getFluid().amount);
		}
		else {
			data.add(0);
		}

		return data;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbtTags)
	{
		super.writeToNBT(nbtTags);
		
		nbtTags.setBoolean("finishedCalc", finishedCalc);

		if(fluidTank.getFluid() != null)
		{
			nbtTags.setTag("fluidTank", fluidTank.writeToNBT(new NBTTagCompound()));
		}
		
		NBTTagList activeList = new NBTTagList();

		for(Coord4D wrapper : activeNodes)
		{
			NBTTagCompound tagCompound = new NBTTagCompound();
			wrapper.write(tagCompound);
			activeList.appendTag(tagCompound);
		}

		if(activeList.tagCount() != 0)
		{
			nbtTags.setTag("activeNodes", activeList);
		}

		NBTTagList usedList = new NBTTagList();

		for(Coord4D obj : usedNodes)
		{
			activeList.appendTag(obj.write(new NBTTagCompound()));
		}

		if(activeList.tagCount() != 0)
		{
			nbtTags.setTag("usedNodes", usedList);
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbtTags)
	{
		super.readFromNBT(nbtTags);
		
		finishedCalc = nbtTags.getBoolean("finishedCalc");

		if(nbtTags.hasKey("fluidTank"))
		{
			fluidTank.readFromNBT(nbtTags.getCompoundTag("fluidTank"));
		}
		
		if(nbtTags.hasKey("activeNodes"))
		{
			NBTTagList tagList = nbtTags.getTagList("activeNodes", NBT.TAG_COMPOUND);

			for(int i = 0; i < tagList.tagCount(); i++)
			{
				activeNodes.add(Coord4D.read((NBTTagCompound)tagList.getCompoundTagAt(i)));
			}
		}

		if(nbtTags.hasKey("usedNodes"))
		{
			NBTTagList tagList = nbtTags.getTagList("usedNodes", NBT.TAG_COMPOUND);

			for(int i = 0; i < tagList.tagCount(); i++)
			{
				usedNodes.add(Coord4D.read((NBTTagCompound)tagList.getCompoundTagAt(i)));
			}
		}
	}

	@Override
	public boolean isItemValidForSlot(int slotID, ItemStack itemstack)
	{
		if(slotID == 1)
		{
			return false;
		}
		else if(slotID == 0)
		{
			return FluidContainerRegistry.isFilledContainer(itemstack);
		}
		else if(slotID == 2)
		{
			return ChargeUtils.canBeDischarged(itemstack);
		}

		return false;
	}

	@Override
	public boolean canExtractItem(int slotID, ItemStack itemstack, EnumFacing side)
	{
		if(slotID == 2)
		{
			return ChargeUtils.canBeOutputted(itemstack, false);
		}
		else if(slotID == 1)
		{
			return true;
		}

		return false;
	}

	@Override
	public EnumSet<EnumFacing> getConsumingSides()
	{
		return EnumSet.of(facing.getOpposite());
	}

	@Override
	public boolean canSetFacing(int side)
	{
		return side != 0 && side != 1;
	}

	@Override
	public int[] getSlotsForFace(EnumFacing side)
	{
		if(side == EnumFacing.UP)
		{
			return new int[] {0};
		}
		else if(side == EnumFacing.DOWN)
		{
			return new int[] {1};
		}
		else {
			return new int[] {2};
		}
	}

	@Override
	public FluidTankInfo[] getTankInfo(EnumFacing direction)
	{
		if(direction == EnumFacing.UP)
		{
			return new FluidTankInfo[] {fluidTank.getInfo()};
		}

		return PipeUtils.EMPTY;
	}

	@Override
	public void setFluidStack(FluidStack fluidStack, Object... data)
	{
		fluidTank.setFluid(fluidStack);
	}

	@Override
	public FluidStack getFluidStack(Object... data)
	{
		return fluidTank.getFluid();
	}

	@Override
	public boolean hasTank(Object... data)
	{
		return true;
	}

	@Override
	public FluidStack drain(EnumFacing from, FluidStack resource, boolean doDrain)
	{
		if(fluidTank.getFluid() != null && fluidTank.getFluid().getFluid() == resource.getFluid() && from == EnumFacing.UP)
		{
			return drain(from, resource.amount, doDrain);
		}

		return null;
	}

	@Override
	public int fill(EnumFacing from, FluidStack resource, boolean doFill)
	{
		if(from == EnumFacing.UP && resource.getFluid().canBePlacedInWorld())
		{
			return fluidTank.fill(resource, true);
		}
		
		return 0;
	}

	@Override
	public FluidStack drain(EnumFacing from, int maxDrain, boolean doDrain)
	{
		return null;
	}

	@Override
	public boolean canFill(EnumFacing from, Fluid fluid)
	{
		return from == EnumFacing.UP && fluid.canBePlacedInWorld();
	}

	@Override
	public boolean canDrain(EnumFacing from, Fluid fluid)
	{
		return false;
	}
	
	@Override
	public boolean onSneakRightClick(EntityPlayer player, EnumFacing side)
	{
		activeNodes.clear();
		usedNodes.clear();
		finishedCalc = false;
		
		player.addChatMessage(new ChatComponentText(EnumColor.DARK_BLUE + "[Mekanism] " + EnumColor.GREY + LangUtils.localize("tooltip.configurator.plenisherReset")));

		return true;
	}

	@Override
	public boolean onRightClick(EntityPlayer player, EnumFacing side)
	{
		return false;
	}

	private static final String[] methods = new String[] {"reset"};

	@Override
	public String[] getMethods()
	{
		return methods;
	}

	@Override
	public Object[] invoke(int method, Object[] arguments) throws Exception
    {
        switch(method)
        {
            case 0:
                activeNodes.clear();
                usedNodes.clear();
                finishedCalc = false;

                return new Object[]{"Plenisher calculation reset."};
            default:
                throw new NoSuchMethodException();
        }
    }
}
