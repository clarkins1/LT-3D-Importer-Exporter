package net.timardo.lt3dimporter;

import com.creativemd.creativecore.client.rendering.RenderBox;
import com.creativemd.creativecore.client.rendering.model.CreativeBakedModel;
import com.creativemd.creativecore.common.utils.mc.ColorUtils;
import com.creativemd.littletiles.common.item.ItemLittleRecipeAdvanced;
import com.creativemd.littletiles.common.tile.preview.LittlePreview;
import com.creativemd.littletiles.common.util.grid.LittleGridContext;

import de.javagl.obj.Mtl;
import de.javagl.obj.MtlWriter;
import de.javagl.obj.Mtls;
import de.javagl.obj.Obj;
import de.javagl.obj.ObjFace;
import de.javagl.obj.ObjWriter;
import de.javagl.obj.Objs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.timardo.lt3dimporter.obj3d.LightObjFace;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.ArrayUtils;

import static net.timardo.lt3dimporter.Utils.*;

public class Exporter extends Thread {
    
    private ICommandSender sender;

    public Exporter(MinecraftServer server, ICommandSender sender) {
        this.sender = sender;
    }
    
    @Override
    public void run() {
        System.out.println("exporting");
        ItemStack item = ((EntityPlayer)this.sender).getHeldItemMainhand();
        
        if (item == ItemStack.EMPTY || !(item.getItem() instanceof ItemLittleRecipeAdvanced)) {
            postMessage(this.sender, TextFormatting.RED + "Empty hand or not a blueprint!");
            return;
        }
        
        Obj obj = Objs.create();
        List<Mtl> mtls = new ArrayList<Mtl>();
        exportModel(item, obj, mtls);

        try {
            OutputStream mtlOutputStream = new FileOutputStream("exported.mtl");
            MtlWriter.write(mtls, mtlOutputStream);
            OutputStream objOutputStream = new FileOutputStream("exported.obj");
            obj.setMtlFileNames(Arrays.asList("exported.mtl"));
            ObjWriter.write(obj, objOutputStream);
            mtlOutputStream.close();
            objOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Method to convert {@link ItemStack} data to a 3D model. Method has bunch of comments mainly for me becuase I know I would get lost easily in that mess
     * 
     * @param stack - stack containing an LT structure
     * @param obj - {@link Obj} in which the result should be written
     * @param mtls - materials for the obj
     */
    public void exportModel(ItemStack stack, Obj obj, List<Mtl> mtls) {
        int grid = LittleGridContext.get(stack.getTagCompound()).size;
        List<? extends RenderBox> cubes = LittlePreview.getCubes(stack, false);
        Map<Long, Integer> vertices = new HashMap<Long, Integer>(); // all vertices with their indices stored as Long from BlockPos TODO check RAM usage/possibility of buffering/file cache
        Map<Long, Integer> textureCoords = new HashMap<Long, Integer>(); // all texture coordinates with their indices stored as Long from two float values
        Map<Integer, Integer> normalMap = new HashMap<Integer, Integer>(); // all normals
        Map<String, Map<Integer, Integer>> textures = new HashMap<String, Map<Integer, Integer>>(); // all textures in format texturename->map of different colors mapped to their indices
        Map<Long, Map<Long, int[]>> uniqueFaces = new HashMap<Long, Map<Long, int[]>>(); // unique faces - if there is a duplicate face, void them (most probably none of them will be rendered anyways)
        
        for (int i = 0; i < cubes.size(); i++) {
            RenderBox cube = cubes.get(i);
            IBakedModel blockModel = Minecraft.getMinecraft().getBlockRendererDispatcher().getModelForState(cube.getBlockState());
            List<BakedQuad> quads = new ArrayList<BakedQuad>(); // all quads making up this tile, should be min. 4 and max. 6
            Arrays.asList(EnumFacing.values()).forEach(facing -> quads.addAll(CreativeBakedModel.getBakedQuad(null, cube, null, cube.getOffset(), cube.getBlockState(), blockModel, null, facing, 0, true)));
            
            for (BakedQuad quad : quads) { // TODO: check grass
                // build data structure
                int[] vertexData = quad.getVertexData(); // data containing position, normals, UV and color (???)
                VertexFormat format = quad.getFormat(); // get format of data, AFAIK should be DefaultVertexFormats.ITEM TODO check if this is always the case
                int index = 0;
                int uvOffset = format.getUvOffsetById(0) / 4; // divided by 4 because offset is in bytes and each integer in int[] has 4 bytes
                int normalOffset = format.getNormalOffset() / 4;
                Set<Long> uniqueVertices = new HashSet<Long>();
                int[] vertexIndices = new int[4];
                int[] texCoordIndices = new int[4];
                int[] normalIndices = new int[4];
                int duplicateOffset = 0;
                
                for (int j = 0; j < 4; j++) { // all quads have 4 vertices, even triangle "looking" ones
                    index = format.getIntegerSize() * j; // real index
                    
                    float x = Float.intBitsToFloat(vertexData[index]);
                    float y = Float.intBitsToFloat(vertexData[index + 1]);
                    float z = Float.intBitsToFloat(vertexData[index + 2]);
                    Long pos = new BlockPos(Math.round(x * grid), Math.round(y * grid), Math.round(z * grid)).toLong(); // store position as long for better performance
                    // skip duplicate vertices and data in case of triangles
                    if (!uniqueVertices.add(pos)) {
                        duplicateOffset--;
                        
                        if (duplicateOffset < -1) break; // skip face to ifnore 2-point faces
                        
                        vertexIndices = Arrays.copyOf(vertexIndices, 4 + duplicateOffset);
                        texCoordIndices = Arrays.copyOf(texCoordIndices, 4 + duplicateOffset);
                        normalIndices = Arrays.copyOf(normalIndices, 4 + duplicateOffset);
                        continue;
                    }
                    
                    float u = quad.getSprite().getUnInterpolatedU(Float.intBitsToFloat(vertexData[index + uvOffset])) / 16.0F % 1; // get U and V from data
                    float v = quad.getSprite().getUnInterpolatedV(Float.intBitsToFloat(vertexData[index + uvOffset + 1])) / 16.0F % 1;
                    Long uv = (((long)Float.floatToRawIntBits(u)) << 32) | (Float.floatToRawIntBits(v) & 0xffffffffL); // store UV as long for better performance
                    
                    int normals = vertexData[index + normalOffset]; // data containing normals, first 3 bytes should be normal data
                    byte normalI = (byte)(normals & 255);
                    byte normalJ = (byte)((normals >> 8) & 255);
                    byte normalK = (byte)((normals >> 16) & 255);
                    
                    // TEXTURE START
                    TextureAtlasSprite sprite = quad.getSprite();
                    String iconName = sprite.getIconName();
                    String matName = iconName.substring(0, iconName.indexOf(':')) + "_" + iconName.substring(iconName.lastIndexOf('/') + 1); //base material name
                    int color = cube.color;
                    boolean buildTexture = false; // TODO maybe put this in a separate method?
                    
                    if (textures.containsKey(iconName)) { // texture is already defined, check color
                        if (!textures.get(iconName).containsKey(color)) { // color is new, create it
                            textures.get(iconName).put(color, textures.get(iconName).size());
                            buildTexture = true;
                        }
                    } else {
                        textures.put(iconName, new HashMap<Integer, Integer>() {{put(color, 0);}});
                        buildTexture = true;
                    }
                    
                    matName = matName + textures.get(iconName).get(color).toString(); // append index of this color as material name
                    
                    if (buildTexture) { // build texture and save it as a file
                        int[][] textureData = sprite.getFrameTextureData(0); // only get the first frame TODO support for animated textures? (would require a blender script for blender probably)
                        int[] rawFinalTextureData = new int[textureData[0].length];
                        
                        for (int k = 0; k < textureData[0].length; k++) { // only getting the first texture data TODO check what the index actually means (constructing more textures into one?)
                            rawFinalTextureData[k] = ColorUtils.blend(textureData[0][k], color); // blend the color
                        }
                        
                        BufferedImage image = new BufferedImage(sprite.getIconWidth(), sprite.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
                        image.setRGB(0, 0, sprite.getIconWidth(), sprite.getIconHeight(), rawFinalTextureData, 0, sprite.getIconWidth());
                        String texturePath = matName.replace('_', '/') + ".png";
                        File textureFile = new File(texturePath);
                        textureFile.mkdirs();

                        try {
                            ImageIO.write(image, "png", textureFile);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        
                        Mtl currentMaterial = Mtls.create(matName); // TODO: add support for more material attributes
                        //currentMaterial.setNs(100.0F); // set by default
                        currentMaterial.setKa(1.0F, 1.0F, 1.0F); // ambient color
                        currentMaterial.setKd(1.0F, 1.0F, 1.0F); // actual color - not used, messes up rendering, which uses different blend technique than LT
                        currentMaterial.setKs(0.5F, 0.5F, 0.5F); // specular reflection
                        // currentMaterial.setKe(0.0F, 0.0F, 0.0F); not supported by Obj lib defines emissive color parameter
                        // currentMaterial.setNi(1.0F); not supported by Obj lib defines optical density parameter
                        currentMaterial.setD(1.0F); // some kind of transparency, idk what it does, transparent textures are pain and very rendering-engine dependant
                        currentMaterial.setMapKd(texturePath);
                        mtls.add(currentMaterial);
                    }
                    
                    obj.setActiveMaterialGroupName(matName);
                    // TEXTURE END
                    
                    if (!vertices.containsKey(pos)) {
                        vertices.put(pos, vertices.size());
                        obj.addVertex(x, y, z);
                    }
                    
                    if (!textureCoords.containsKey(uv)) {
                        textureCoords.put(uv, textureCoords.size());
                        obj.addTexCoord(u, 1.0F - v); // flip v to preserve texture rotation
                    }
                    
                    if (!normalMap.containsKey(normals)) {
                        normalMap.put(normals, normalMap.size());
                        obj.addNormal(normalI / 255.0F, normalJ / 255.0F, normalK / 255.0F);
                    }
                    
                    vertexIndices[j + duplicateOffset] = vertices.get(pos);
                    texCoordIndices[j + duplicateOffset] = textureCoords.get(uv);
                    normalIndices[j + duplicateOffset] = normalMap.get(normals);
                }
                
                if (duplicateOffset >= -1) { // only add face if it's a quad or a triangle
                    Long firstTwo = (((long)vertexIndices[0]) << 32) | (vertexIndices[1] & 0xffffffffL);
                    Long lastTwo = (((long)vertexIndices[2]) << 32) | ((vertexIndices.length == 3 ? -1 : vertexIndices[3]) & 0xffffffffL);
                    
                    if (!uniqueFaces.containsKey(firstTwo)) {
                        uniqueFaces.put(firstTwo, new HashMap<Long, int[]>());
                    }
                    
                    if (!uniqueFaces.get(firstTwo).containsKey(lastTwo)) {
                        int[] otherData = ArrayUtils.addAll(texCoordIndices, normalIndices);
                        uniqueFaces.get(firstTwo).put(lastTwo, otherData); // add data for face
                    } else if (uniqueFaces.get(firstTwo).get(lastTwo) != null) {
                        uniqueFaces.get(firstTwo).put(lastTwo, null); // set face data to null to ignore them later
                    }
                }
            }
        }
        
        for (Entry<Long, Map<Long, int[]>> firstMap : uniqueFaces.entrySet()) {
            long firstTwo = firstMap.getKey();

            for (Entry<Long, int[]> secondMap : firstMap.getValue().entrySet()) {
                if (secondMap.getValue() == null) continue; // ignore empty entries
                
                long secondTwo = secondMap.getKey();
                int[] otherData = secondMap.getValue();
                int[] vertexIndices = new int[] { (int)(firstTwo >> 32), (int)(firstTwo), (int)(secondTwo >> 32), (int)(secondTwo) };
                
                if (vertexIndices[3] == -1) vertexIndices = Arrays.copyOf(vertexIndices, 3); // this is a triangle
                
                int[] texCoordIndices = Arrays.copyOf(otherData, vertexIndices.length);
                int[] normalIndices = new int[vertexIndices.length];
                System.arraycopy(otherData, vertexIndices.length, normalIndices, 0, vertexIndices.length);
                ObjFace objFace = new LightObjFace(vertexIndices, texCoordIndices, normalIndices); // TODO: check normals
                obj.addFace(objFace);
            }
        }
    }
}
