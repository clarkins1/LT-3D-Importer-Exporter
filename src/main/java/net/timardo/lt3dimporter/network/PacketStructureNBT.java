package net.timardo.lt3dimporter.network;

import com.creativemd.creativecore.common.packet.CreativeCorePacket;
import com.creativemd.littletiles.LittleTiles;
import com.creativemd.littletiles.common.action.LittleActionException;
import com.creativemd.littletiles.common.item.ItemRecipeAdvanced;
import com.creativemd.littletiles.common.tile.math.location.StructureLocation;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.timardo.lt3dimporter.LT3DImporter;
import net.timardo.lt3dimporter.littlestructure.ModelImporter;

public class PacketStructureNBT extends CreativeCorePacket {

    private NBTTagCompound nbt;
    
    public PacketStructureNBT() {}
    
    public void setNBT(NBTTagCompound nbt) {
        this.nbt = nbt;
    }
    
    @Override
    public void writeBytes(ByteBuf b) {
        writeNBT(b, this.nbt);
    }

    @Override
    public void readBytes(ByteBuf b) {
        this.nbt = readNBT(b);
    }

    @Override
    public void executeClient(EntityPlayer player) {
        
    }

    @Override
    public void executeServer(EntityPlayer player) {
        if (nbt.hasNoTags()) return;
        
        ModelImporter structure = null;
        StructureLocation loc = new StructureLocation((NBTTagCompound) nbt.getTag("loc"));
        
        try {
            structure = (ModelImporter) loc.find(player.world);
        } catch (LittleActionException lae) {
            LT3DImporter.logger.error("Failed to get structure from packet");
            return;
        }
        
        if (nbt.getBoolean("item")) {
            ItemStack slot = structure.output.inventory.getStackInSlot(0);
            if (!(slot.getItem() instanceof ItemRecipeAdvanced || player.isCreative())) return;
            ItemStack recipe = new ItemStack(LittleTiles.recipeAdvanced);
            recipe.setTagCompound((NBTTagCompound) nbt.getTag("recipe_nbt"));
            structure.output.inventory.setInventorySlotContents(0, recipe);
        } else {
            structure.model = nbt.getString("model");
            structure.texFile = nbt.getString("tex_file");
            structure.color = nbt.getInteger("color");
            structure.maxSize = nbt.getString("max_size");
            structure.gridSize = nbt.getString("grid");
            structure.precision = nbt.getString("precision");
            structure.baseBlock = new ItemStack((NBTTagCompound) nbt.getTag("base_block"));
            structure.useTex = nbt.getBoolean("use_tex");
        }
    }
}
