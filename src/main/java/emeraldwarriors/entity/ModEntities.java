package emeraldwarriors.entity;

import emeraldwarriors.Emerald_Warriors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntities {

    // Clave de registro para 1.21.11 (usa ResourceKey<EntityType<?>> con Identifier como identificador)
    public static final ResourceKey<EntityType<?>> EMERALD_MERCENARY_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath(Emerald_Warriors.MOD_ID, "emerald_mercenary"));

    public static final EntityType<EmeraldMercenaryEntity> EMERALD_MERCENARY = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            EMERALD_MERCENARY_KEY,
            EntityType.Builder.<EmeraldMercenaryEntity>of(EmeraldMercenaryEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build(EMERALD_MERCENARY_KEY)
    );

    public static void registerAttributes() {
        FabricDefaultAttributeRegistry.register(EMERALD_MERCENARY, EmeraldMercenaryEntity.createAttributes());
    }
}
