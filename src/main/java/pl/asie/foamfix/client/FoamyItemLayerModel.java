/**
 * Copyright (C) 2016, 2017, 2018, 2019, 2020, 2021 Adrian Siekierka
 *
 * This file is part of FoamFix.
 *
 * FoamFix is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FoamFix is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FoamFix.  If not, see <http://www.gnu.org/licenses/>.
 */
package pl.asie.foamfix.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ItemLayerModel;
import net.minecraftforge.client.model.PerspectiveMapWrapper;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.commons.lang3.tuple.Pair;
import pl.asie.foamfix.util.MethodHandleHelper;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.ref.SoftReference;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class FoamyItemLayerModel implements IModel {
    private static final ResourceLocation MISSINGNO = new ResourceLocation("missingno");
    private static final MethodHandle OVERRIDES_GET;
    private static final MethodHandle BUILD_QUAD;
    private final ItemLayerModel parent;

    static {
        MethodHandle handle = null;
        try {
            handle = MethodHandles.lookup().unreflect(ReflectionHelper.findMethod(
                    ItemLayerModel.class, "buildQuad", "buildQuad",
                    VertexFormat.class, Optional.class, EnumFacing.class, TextureAtlasSprite.class, int.class,
                    float.class, float.class,float.class,float.class,float.class,
                    float.class, float.class,float.class,float.class,float.class,
                    float.class, float.class,float.class,float.class,float.class,
                    float.class, float.class,float.class,float.class,float.class));
        } catch (Exception e) {
            // We don't need this (there's a slow fallback route), so just warn the user.
            e.printStackTrace();
        }
        BUILD_QUAD = handle;

        handle = null;
        try {
            handle = MethodHandleHelper.findFieldGetter(ItemLayerModel.class, "overrides");
        } catch (Exception e) {
            // We DO need THIS, so throw a runtime exception if we can't have it.
            throw new RuntimeException(e);
        }
        OVERRIDES_GET = handle;
    }

    public static class Static3DItemModel implements IBakedModel {
        private final ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms;
        private final VertexFormat format;
        private final TextureAtlasSprite particle;
        private final ItemOverrideList overrides;
        private final List<TextureAtlasSprite> textures;
        private final Optional<TRSRTransformation> transform;

        private SoftReference<List<BakedQuad>> quadsSoft = null;

        public Static3DItemModel(ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms,
                                 VertexFormat format, TextureAtlasSprite particle, ItemOverrideList overrides,
                                 List<TextureAtlasSprite> textures, Optional<TRSRTransformation> transform) {
            this.transforms = transforms;
            this.format = format;
            this.particle = particle;
            this.overrides = overrides;
            this.textures = textures;
            this.transform = transform;
        }

        @Override
        public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
            Pair<? extends IBakedModel, Matrix4f> pair = PerspectiveMapWrapper.handlePerspective(this, transforms, type);

            return pair;
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
            if (quadsSoft == null || quadsSoft.get() == null) {
                ImmutableList.Builder<BakedQuad> builder = new ImmutableList.Builder<>();

                for (int i = 0; i < textures.size(); i++) {
                    TextureAtlasSprite sprite = textures.get(i);
                    builder.addAll(ItemLayerModel.getQuadsForSprite(i, sprite, format, transform));
                }

                quadsSoft = new SoftReference<>(builder.build());
            }

            return side == null ? quadsSoft.get() : Collections.EMPTY_LIST;
        }

        @Override
        public boolean isAmbientOcclusion() {
            return true;
        }

        @Override
        public boolean isGui3d() {
            return false;
        }

        @Override
        public boolean isBuiltInRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleTexture() {
            return particle;
        }

        @Override
        public ItemOverrideList getOverrides() {
            return overrides;
        }
    }

    public static class Dynamic3DItemModel implements IBakedModel {
        private final DynamicItemModel parent;
        private SoftReference<List<BakedQuad>> quadsSoft = null;

        public Dynamic3DItemModel(DynamicItemModel parent) {
            this.parent = parent;
        }

        @Override
        public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
            Pair<? extends IBakedModel, Matrix4f> pair = PerspectiveMapWrapper.handlePerspective(this, parent.transforms, type);

            if (type == ItemCameraTransforms.TransformType.GUI && pair.getRight() == null) {
                return Pair.of(parent, null);
            }

            return pair;
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
            if (quadsSoft == null || quadsSoft.get() == null) {
                ImmutableList.Builder<BakedQuad> builder = new ImmutableList.Builder<>();

                for (int i = 0; i < parent.textures.size(); i++) {
                    TextureAtlasSprite sprite = parent.textures.get(i);
                    builder.addAll(ItemLayerModel.getQuadsForSprite(i, sprite, parent.format, parent.transform));
                }

                quadsSoft = new SoftReference<>(builder.build());
            }

            return side == null ? quadsSoft.get() : Collections.EMPTY_LIST;
        }

        @Override
        public boolean isAmbientOcclusion() {
            return true;
        }

        @Override
        public boolean isGui3d() {
            return false;
        }

        @Override
        public boolean isBuiltInRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleTexture() {
            return parent.particle;
        }

        @Override
        public ItemOverrideList getOverrides() {
            return parent.overrides;
        }
    }

    public static class DynamicItemModel implements IBakedModel {
        private final List<TextureAtlasSprite> textures;
        private final TextureAtlasSprite particle;
        private final ImmutableList<BakedQuad> fastQuads;
        private final ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms;
        private final VertexFormat format;
        private final Optional<TRSRTransformation> transform;
        private final IBakedModel otherModel;
        private final ItemOverrideList overrides;

        public DynamicItemModel(ImmutableList<BakedQuad> quads, TextureAtlasSprite particle,
                                ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms,
                                ItemOverrideList overrides, List<TextureAtlasSprite> textures,
                                VertexFormat format, Optional<TRSRTransformation> transform) {
            this.fastQuads = quads;
            this.particle = particle;
            this.transforms = transforms;
            this.overrides = overrides;
            this.textures = textures;
            this.format = format;
            this.transform = transform;
            this.otherModel = new Dynamic3DItemModel(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
            return side == null ? fastQuads : Collections.EMPTY_LIST;
        }

        @Override
        public boolean isAmbientOcclusion() {
            return true;
        }

        @Override
        public boolean isGui3d() {
            return false;
        }

        @Override
        public boolean isBuiltInRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleTexture() {
            return particle;
        }

        @Override
        public ItemOverrideList getOverrides() {
            return overrides;
        }

        @Override
        public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
            Pair<? extends IBakedModel, Matrix4f> pair = PerspectiveMapWrapper.handlePerspective(this, transforms, type);

            if (type != ItemCameraTransforms.TransformType.GUI) {
                return Pair.of(otherModel, pair.getRight());
            }

            return pair;
        }
    }

    public FoamyItemLayerModel(ItemLayerModel parent) {
        this.parent = parent;
    }

    @Override
    public IModel retexture(final ImmutableMap<String, String> textures) {
        return new FoamyItemLayerModel(parent.retexture(textures));
    }

    @Override
    public Collection<ResourceLocation> getDependencies() {
        return parent.getDependencies();
    }

    @Override
    public Collection<ResourceLocation> getTextures() {
        return parent.getTextures();
    }

    @Override
    public IBakedModel bake(IModelState state, final VertexFormat format, Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        return bakeStatic(parent, state, format, bakedTextureGetter);
    }

    public static IBakedModel bakeStatic(ItemLayerModel parent, final IModelState state, final VertexFormat format, final Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> map = PerspectiveMapWrapper.getTransforms(state);
        TRSRTransformation guiTransform = map.get(ItemCameraTransforms.TransformType.GUI);

        ImmutableList.Builder<BakedQuad> builder = ImmutableList.builder();
        Optional<TRSRTransformation> transform = state.apply(Optional.empty());
        List<ResourceLocation> textures = (List<ResourceLocation>) parent.getTextures();
        ImmutableList.Builder<TextureAtlasSprite> textureAtlas = new ImmutableList.Builder<>();
        TextureAtlasSprite particle = bakedTextureGetter.apply(textures.isEmpty() ? MISSINGNO : textures.get(0));
        ItemOverrideList list;
        try {
            list = (ItemOverrideList) OVERRIDES_GET.invokeExact(parent);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

        if (guiTransform != null && !guiTransform.isIdentity()) {
            // Have only 3D model
            for (int i = 0; i < textures.size(); i++) {
                TextureAtlasSprite sprite = bakedTextureGetter.apply(textures.get(i));
                textureAtlas.add(sprite);
            }

            return new Static3DItemModel(map, format, particle, list, textureAtlas.build(), transform);
        } else {
            if (BUILD_QUAD != null) {
                // Fast route!
                for (int i = 0; i < textures.size(); i++) {
                    TextureAtlasSprite sprite = bakedTextureGetter.apply(textures.get(i));
                    textureAtlas.add(sprite);
                    try {
                        builder.add((BakedQuad) BUILD_QUAD.invokeExact(format, transform, EnumFacing.SOUTH, sprite, i,
                                0f, 0f, 8.5f / 16f, (float) sprite.getMinU(), (float) sprite.getMaxV(),
                                1f, 0f, 8.5f / 16f, (float) sprite.getMaxU(), (float) sprite.getMaxV(),
                                1f, 1f, 8.5f / 16f, (float) sprite.getMaxU(), (float) sprite.getMinV(),
                                0f, 1f, 8.5f / 16f, (float) sprite.getMinU(), (float) sprite.getMinV()
                        ));
                    } catch (Throwable t) {
                        throw new RuntimeException(t);
                    }
                }
            } else {
                // Slow fallback route :-(
                for (int i = 0; i < textures.size(); i++) {
                    TextureAtlasSprite sprite = bakedTextureGetter.apply(textures.get(i));
                    textureAtlas.add(sprite);

                    for (BakedQuad quad : ItemLayerModel.getQuadsForSprite(i, sprite, format, transform)) {
                        if (quad.getFace() == EnumFacing.SOUTH)
                            builder.add(quad);
                    }
                }
            }

            // This returns otherModel because the 3D model is default
            return new DynamicItemModel(builder.build(), particle, map, list, textureAtlas.build(), format, transform).otherModel;
        }
    }

    @Override
    public IModelState getDefaultState() {
        return TRSRTransformation.identity();
    }
}
