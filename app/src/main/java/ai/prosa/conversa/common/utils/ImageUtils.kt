package ai.prosa.conversa.common.utils

import ai.prosa.conversa.R
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import id.zelory.compressor.Compressor
import java.io.File


//@GlideModule
//class MyAppGlideModule : AppGlideModule() {
//    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
//        registry.replace(
//            GlideUrl::class.java, InputStream::class.java,
//            OkHttpUrlLoader.Factory(ConversaHttpClient)
//        )
//    }
//}

object ImageUtils {
    suspend fun compress(context: Context, originalFile: File): File {
        return Compressor.compress(context, originalFile)
    }

    fun showImage(src: String?, root: View, imageView: ImageView) {
        Glide.with(root.context)
            .asBitmap()
            .load(src)
            .into(imageView)

        imageView.setOnClickListener {
            val (builder, view) = ViewUtils.createDialog(
                root.context,
                root,
                R.layout.conversa_dialogue_image_show_full
            )
            builder.setCancelable(true)

            val imageViewFull = view.findViewById<ImageView>(R.id.image)
            Glide.with(root.context)
                .asBitmap()
                .load(src)
                .into(imageViewFull)

            val dialog = builder.show().also {
                it.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
            view.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener {
                dialog.cancel()
            }
        }
    }
}