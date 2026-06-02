package com.kstream.tv.image

import android.content.Context
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions
import com.kstream.tv.tier.DeviceTier

/**
 * Tier-aware Glide configuration.
 *
 * Memory budgets (fraction of app heap):
 *  - LOW : ~8%  memory cache, RGB_565 decode (saves 50% bitmap memory)
 *  - MID : ~12% memory cache, ARGB_8888
 *  - HIGH: ~15% memory cache, ARGB_8888
 *
 * Disk cache uses Glide defaults (~250 MB) on all tiers — TV devices don't
 * thrash like phones, and a warm disk cache means instant card art on rail scroll.
 *
 * AppGlideModule must be a public final class with the @GlideModule annotation;
 * the KSP processor generates `GeneratedAppGlideModuleImpl` at build time which
 * `Glide.get(context)` resolves on first use.
 */
@GlideModule
class KStreamGlideModule : AppGlideModule() {

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        val tier = DeviceTier.get(context)

        val memoryFraction = when (tier) {
            DeviceTier.LOW -> 0.08f
            DeviceTier.MID -> 0.12f
            DeviceTier.HIGH -> 0.15f
        }
        val lowMemFraction = when (tier) {
            DeviceTier.LOW -> 0.05f
            DeviceTier.MID -> 0.08f
            DeviceTier.HIGH -> 0.10f
        }

        val calc = MemorySizeCalculator.Builder(context)
            .setMemoryCacheScreens(if (tier == DeviceTier.LOW) 1.0f else 1.5f)
            .setBitmapPoolScreens(if (tier == DeviceTier.LOW) 1.0f else 1.5f)
            .setMaxSizeMultiplier(memoryFraction)
            .setLowMemoryMaxSizeMultiplier(lowMemFraction)
            .build()

        builder.setMemoryCache(LruResourceCache(calc.memoryCacheSize.toLong()))
        builder.setBitmapPool(LruBitmapPool(calc.bitmapPoolSize.toLong()))

        val decodeFormat = if (tier == DeviceTier.LOW) {
            DecodeFormat.PREFER_RGB_565
        } else {
            DecodeFormat.PREFER_ARGB_8888
        }
        builder.setDefaultRequestOptions(
            RequestOptions()
                .format(decodeFormat)
                .disallowHardwareConfig()
        )
    }

    /**
     * We do not consume manifest-declared GlideModules from any library —
     * disabling the legacy parser shaves startup time and prevents duplicate-
     * registration errors with libraries that ship their own GlideModule.
     */
    override fun isManifestParsingEnabled(): Boolean = false
}
