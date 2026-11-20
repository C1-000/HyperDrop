package cythen.hyperdrop.utils

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import cythen.hyperdrop.R
import cythen.hyperdrop.config.DeviceImageCatalog
import cythen.hyperdrop.config.DeviceImageSet
import cythen.hyperdrop.config.PodImagePrefs
import cythen.hyperdrop.config.PodImageResource
import cythen.hyperdrop.config.imageUri
import cythen.hyperdrop.device.DeviceModels

object PodImageLoader {
    private const val MODULE_PACKAGE = "cythen.hyperdrop"

    fun loadBitmap(
        context: Context,
        prefs: SharedPreferences,
        address: String,
        resource: PodImageResource,
        fallbackResId: Int,
    ): Bitmap? {
        val earphone = runCatching { PodImagePrefs.findOrLatest(prefs, address) }.getOrNull()
        val custom = runCatching {
            earphone?.imageUri(resource)?.let { uri -> decodeUri(context, uri) }
        }.getOrNull()
        if (custom != null) {
            //android.util.Log.d("HyperDrop-PodImage", "loaded custom $resource for ${earphone?.address}")
            return custom
        }

        val moduleContext = runCatching {
            context.createPackageContext(MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull() ?: return null
        return BitmapFactory.decodeResource(moduleContext.resources, resolveFallbackRes(prefs, address, resource, fallbackResId))
    }

    /**
     * Pick the built-in drawable for a device+colourway. Custom images (handled above)
     * always win; this only resolves the bundled fallback art.
     */
    private fun resolveFallbackRes(
        prefs: SharedPreferences,
        address: String,
        resource: PodImageResource,
        genericResId: Int,
    ): Int {
        val earphone = runCatching { PodImagePrefs.findOrLatest(prefs, address) }.getOrNull()
        if (earphone == null) return genericResId
        val profile = DeviceModels.match(earphone.name) ?: return genericResId
        val art: DeviceImageSet = DeviceImageCatalog.resolve(profile.imageKey, earphone.colorVariant)
        return when (resource) {
            PodImageResource.BOX -> art.box
            PodImageResource.LEFT -> art.left
            PodImageResource.RIGHT -> art.right
        }
    }

    fun loadBitmapWithCustomFallback(
        context: Context,
        prefs: SharedPreferences,
        address: String,
        resource: PodImageResource,
        customFallbackResource: PodImageResource,
        fallbackResId: Int,
    ): Bitmap? {
        val earphone = runCatching { PodImagePrefs.findOrLatest(prefs, address) }.getOrNull()
        val custom = runCatching {
            earphone?.imageUri(resource)?.let { uri -> decodeUri(context, uri) }
                ?: earphone?.imageUri(customFallbackResource)?.let { uri -> decodeUri(context, uri) }
        }.getOrNull()
        if (custom != null) {
            //android.util.Log.d("HyperDrop-PodImage", "loaded custom $resource for ${earphone?.address}")
            return custom
        }

        val moduleContext = runCatching {
            context.createPackageContext(MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull() ?: return null
        return BitmapFactory.decodeResource(
            moduleContext.resources,
            resolveFallbackRes(prefs, address, resource, fallbackResId),
        )
    }

    fun loadBoxBitmap(context: Context, prefs: SharedPreferences, address: String): Bitmap? {
        return loadBitmap(context, prefs, address, PodImageResource.BOX, R.drawable.img_box)
    }


    fun loadIslandLeftBitmap(context: Context, prefs: SharedPreferences, address: String): Bitmap? {
        return loadBitmapWithCustomFallback(
            context = context,
            prefs = prefs,
            address = address,
            resource = PodImageResource.LEFT,
            customFallbackResource = PodImageResource.BOX,
            fallbackResId = R.drawable.img_left,
        )
    }

    fun loadIslandRightBitmap(context: Context, prefs: SharedPreferences, address: String): Bitmap? {
        return loadBitmapWithCustomFallback(
            context = context,
            prefs = prefs,
            address = address,
            resource = PodImageResource.RIGHT,
            customFallbackResource = PodImageResource.BOX,
            fallbackResId = R.drawable.img_right,
        )
    }

    private fun decodeUri(context: Context, uri: android.net.Uri): Bitmap? {
        return runCatching {
            context.contentResolver.openInputStream(uri).use { input ->
                input?.let { BitmapFactory.decodeStream(it) }
            }
        }.getOrNull()
    }
}
