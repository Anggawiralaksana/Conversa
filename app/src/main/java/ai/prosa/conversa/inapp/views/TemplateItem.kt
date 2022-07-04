package ai.prosa.conversa.inapp.views

import ai.prosa.conversa.R
import ai.prosa.conversa.databinding.ConversaItemTemplateAddButtonBinding
import ai.prosa.conversa.databinding.ConversaItemTemplateMessageBinding
import ai.prosa.conversa.inapp.data.db.TemplateMessage
import android.view.View
import com.xwray.groupie.databinding.BindableItem

class TemplateItem(
    val item: TemplateMessage,
    private val isDriver: Boolean,
    val onClickListener: (String, View, Int) -> Unit,
    val onActionClickListener: (TemplateMessage) -> Unit
) :
    BindableItem<ConversaItemTemplateMessageBinding>() {
    override fun bind(viewBinding: ConversaItemTemplateMessageBinding, position: Int) {
        viewBinding.templateText.text = item.text
        viewBinding.root.setOnClickListener {
            onClickListener(item.text, it, position)
        }
        if (isDriver) {
            viewBinding.templateBtn.visibility = View.INVISIBLE
        } else {
            viewBinding.templateBtn.setOnClickListener {
                onActionClickListener(item)
            }
        }
    }

    override fun getLayout(): Int {
        return R.layout.conversa_item_template_message
    }
}

class NewTemplateButton(
    val onClickListener: (Int) -> Unit,
    private val templateLeftString: String
) :
    BindableItem<ConversaItemTemplateAddButtonBinding>() {
    override fun bind(viewBinding: ConversaItemTemplateAddButtonBinding, position: Int) {
        viewBinding.root.setOnClickListener {
            onClickListener(position)
        }
        viewBinding.templateLeft.text = templateLeftString
    }

    override fun getLayout(): Int {
        return R.layout.conversa_item_template_add_button
    }
}