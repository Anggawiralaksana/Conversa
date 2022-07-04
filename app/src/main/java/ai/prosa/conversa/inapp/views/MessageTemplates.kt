package ai.prosa.conversa.inapp.views

import ai.prosa.conversa.R
import ai.prosa.conversa.common.utils.ViewUtils
import ai.prosa.conversa.inapp.InappDialogueFragment
import ai.prosa.conversa.inapp.data.db.TemplateMessage
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.xwray.groupie.Group

const val MAX_TEMPLATE_MESSAGE_LENGTH = 140
const val MAX_TEMPLATE = 5

fun InappDialogueFragment.resetSelectedTemplate() {
    if (this.selectedTemplateIndex >= 0) {
        val oldTextView =
            binding.messageTemplatesRecView[this.selectedTemplateIndex].findViewById<ConstraintLayout>(
                R.id.container
            )
        if (oldTextView != null) {
            oldTextView.background =
                AppCompatResources.getDrawable(
                    oldTextView.context,
                    R.drawable.conversa_bg_rounded_corner_grey
                )
            oldTextView.findViewById<TextView>(R.id.templateText)
                .setTextColor(ContextCompat.getColor(oldTextView.context, R.color.black2))
            oldTextView.findViewById<MaterialButton>(R.id.templateBtn)
                .setIconTintResource(R.color.black2)
        }
    }
    this.selectedTemplateIndex = -1
}

fun InappDialogueFragment.setupMessageTemplates() {
    val binding = this.binding
    val uiViewModel = this.vm
    val requireContext = this.requireContext()
    val requireView = this.requireView()

    fun applySelectedTemplateView(view: View) {
        this.resetSelectedTemplate()

        val textView = view.findViewById<ConstraintLayout>(R.id.container)
        textView.background =
            AppCompatResources.getDrawable(textView.context, R.drawable.conversa_bg_rounded_corner_blue)
        textView.findViewById<TextView>(R.id.templateText)
            .setTextColor(ContextCompat.getColor(textView.context, R.color.white))
        textView.findViewById<MaterialButton>(R.id.templateBtn)
            .setIconTintResource(R.color.white)
    }

    // select the clicked template
    val templateOnClickListener = { text: String, view: View, index: Int ->
        // TODO: set cursor to be at the end of the input field
        binding.editMessage.editText?.setText(text)
        applySelectedTemplateView(view)
        this.selectedTemplateIndex = index
    }

    val onActionClickListener = { item: TemplateMessage ->
        val (builder, dialogView) = ViewUtils.createDialog(
            requireContext,
            requireView,
            R.layout.conversa_dialogue_template_action
        )
        val actionDialog = builder.show().also {
            it.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val wlp = it.window?.attributes
            wlp?.gravity = Gravity.BOTTOM

            it.window?.attributes = wlp
        }

        dialogView.findViewById<MaterialButton>(R.id.deleteBtn).setOnClickListener {
            actionDialog.cancel()
            val (deleteBuilder, deleteDialogView) = ViewUtils.createDialog(
                requireContext,
                requireView,
                R.layout.conversa_dialogue_template_delete
            )
            val deleteDialog = deleteBuilder.show().also {
                it.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }

            val yesBtn = deleteDialogView.findViewById<MaterialButton>(R.id.yesBtn)
            val cancelBtn = deleteDialogView.findViewById<MaterialButton>(R.id.cancelBtn)

            cancelBtn.setOnClickListener {
                deleteDialog.cancel()
            }

            yesBtn.setOnClickListener {
                this.resetSelectedTemplate()
                uiViewModel.deleteTemplateMessage(item.id)
                binding.editMessageInput.setText("")
                deleteDialog.cancel()
                actionDialog.cancel()
            }
        }

        dialogView.findViewById<MaterialButton>(R.id.editBtn).setOnClickListener {
            actionDialog.cancel()
            val (editBuilder, editDialogView) = ViewUtils.createDialog(
                requireContext,
                requireView,
                R.layout.conversa_dialogue_template_edit
            )
            editBuilder.setView(editDialogView)

            val inputView =
                editDialogView.findViewById<TextInputEditText>(R.id.messageTemplateTextInput)
            inputView.setText(item.text)
            val editDialog = editBuilder.show().also {
                it.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
            editDialogView.findViewById<MaterialButton>(R.id.cancelBtn).setOnClickListener {
                editDialog.cancel()
            }

            // TODO: refactor this and add template; its the same code
            val leftLengthView = editDialogView.findViewById<TextView>(R.id.lengthLeft)
            inputView.filters = arrayOf(InputFilter.LengthFilter(MAX_TEMPLATE_MESSAGE_LENGTH))
            leftLengthView.text = (MAX_TEMPLATE_MESSAGE_LENGTH - item.text.length).toString()

            inputView.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {

                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    s?.let { text ->
                        leftLengthView.text =
                            (MAX_TEMPLATE_MESSAGE_LENGTH - text.length).toString()
                    }
                }

                override fun afterTextChanged(s: Editable?) {
                }
            })

            editDialogView.findViewById<MaterialButton>(R.id.saveBtn).setOnClickListener {
                binding.editMessageInput.setText("")
                this.resetSelectedTemplate()
                uiViewModel.updateTemplateMessage(item.id, inputView.text.toString())
                editDialog.cancel()
                actionDialog.cancel()
            }
        }
    }

    fun bindTemplates(it: List<TemplateMessage>) {
        val templates: MutableList<Group> = it.map { t ->
            TemplateItem(t, isDriver, templateOnClickListener, onActionClickListener)
        }.toMutableList()

        if (it.size < MAX_TEMPLATE && !isDriver) {
            val newTemplateOnClickListener = { _: Int ->
                val (builder, view) = ViewUtils.createDialog(
                    requireContext,
                    requireView,
                    R.layout.conversa_dialogue_template_add
                )
                val dialog = builder.show()
                view.findViewById<MaterialButton>(R.id.cancelBtn).setOnClickListener {
                    dialog.cancel()
                }

                val inputView =
                    view.findViewById<TextInputEditText>(R.id.messageTemplateTextInput)
                view.findViewById<MaterialButton>(R.id.addBtn).setOnClickListener {
                    uiViewModel.insertTemplateMessage(inputView.text.toString())
                    dialog.cancel()
                }

                val leftLengthView = view.findViewById<TextView>(R.id.lengthLeft)
                inputView.filters =
                    arrayOf(InputFilter.LengthFilter(MAX_TEMPLATE_MESSAGE_LENGTH))
                leftLengthView.text = MAX_TEMPLATE_MESSAGE_LENGTH.toString()

                inputView.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {

                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                        s?.let { text ->
                            leftLengthView.text =
                                (MAX_TEMPLATE_MESSAGE_LENGTH - text.length).toString()
                        }
                    }

                    override fun afterTextChanged(s: Editable?) {
                    }
                })

                dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                Unit
            }
            templates.add(
                NewTemplateButton(
                    newTemplateOnClickListener,
                    this.getString(R.string.template_left, MAX_TEMPLATE - it.size)
                )
            )
            binding.editMessageInput.setText("")
            this.resetSelectedTemplate()
        }
        this.templateMessageAdapter.update(templates)
    }

    if (isDriver) {
        uiViewModel.getDriverTemplateMessages {
            bindTemplates(it)
        }
    } else {
        uiViewModel.templateMessages.observe(this.viewLifecycleOwner) {
            bindTemplates(it)
        }
    }

    binding.messageTemplatesRecView.adapter = this.templateMessageAdapter

    val layoutManager = LinearLayoutManager(requireContext)
    binding.messageTemplatesRecView.layoutManager = layoutManager
    binding.messageTemplatesRecView.visibility = View.GONE

    binding.messageTemplates.setOnClickListener {
        if (binding.messageTemplatesRecView.visibility == View.GONE) {
            binding.messageTemplatesRecView.visibility = View.VISIBLE
            binding.messageTemplates.icon =
                AppCompatResources.getDrawable(requireContext, R.drawable.conversa_ic_keyboard)
        } else {
            binding.messageTemplatesRecView.visibility = View.GONE
            binding.messageTemplates.icon =
                AppCompatResources.getDrawable(requireContext, R.drawable.conversa_ic_chat)
        }
    }
}
