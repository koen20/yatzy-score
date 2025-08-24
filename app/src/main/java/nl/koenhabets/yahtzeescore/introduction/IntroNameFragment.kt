package nl.koenhabets.yahtzeescore.introduction

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.github.appintro.SlidePolicy
import nl.koenhabets.yahtzeescore.R

class IntroNameFragment : Fragment(), SlidePolicy {
    private lateinit var editText: EditText
    private lateinit var tvError: TextView
    private var text: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_intro_name, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        editText = view.findViewById(R.id.editTextStartName)
        tvError = view.findViewById(R.id.textViewStartNameError)
    }

    override val isPolicyRespected: Boolean
        get() = editText.text.toString().trim() != ""

    override fun onUserIllegallyRequestedNextPage() {
        tvError.text = getString(R.string.name_required_error)
    }

    fun getName(): String {
        return if (::editText.isInitialized) editText.text.toString() else ""
    }

    companion object {
        fun newInstance(): IntroNameFragment {
            return IntroNameFragment()
        }
    }
}