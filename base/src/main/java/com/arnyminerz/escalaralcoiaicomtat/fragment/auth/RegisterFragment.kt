package com.arnyminerz.escalaralcoiaicomtat.fragment.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.arnyminerz.escalaralcoiaicomtat.R
import com.arnyminerz.escalaralcoiaicomtat.activity.profile.AuthActivity
import com.arnyminerz.escalaralcoiaicomtat.databinding.FragmentAuthRegisterBinding
import com.arnyminerz.escalaralcoiaicomtat.generic.toast
import com.arnyminerz.escalaralcoiaicomtat.list.viewListOf
import com.arnyminerz.escalaralcoiaicomtat.shared.LOGGED_IN_REQUEST_CODE
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import timber.log.Timber

class RegisterFragment private constructor() : Fragment() {
    private var _binding: FragmentAuthRegisterBinding? = null

    private val binding: FragmentAuthRegisterBinding = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.emailEditText.setOnEditorActionListener { _, _, _ ->
            binding.displayNameEditText.requestFocus()

            true
        }
        binding.emailEditText.setOnFocusChangeListener { _, focused ->
            if (focused) {
                binding.emailTextField.error = null
                binding.emailTextField.isErrorEnabled = false
            }
        }

        binding.displayNameEditText.setOnEditorActionListener { _, _, _ ->
            binding.passwordEditText.requestFocus()

            true
        }
        binding.displayNameEditText.setOnFocusChangeListener { _, focused ->
            if (focused) {
                binding.displayNameTextField.error = null
                binding.displayNameTextField.isErrorEnabled = false
            }
        }

        binding.passwordEditText.setOnEditorActionListener { _, _, _ ->
            binding.passwordConfirmEditText.requestFocus()

            true
        }
        binding.passwordEditText.setOnFocusChangeListener { _, focused ->
            if (focused) {
                binding.passwordTextField.error = null
                binding.passwordTextField.isErrorEnabled = false
            }
        }

        binding.passwordConfirmEditText.setOnEditorActionListener { _, _, _ ->
            binding.registerButton.performClick()

            true
        }
        binding.passwordConfirmEditText.setOnFocusChangeListener { _, focused ->
            if (focused) {
                binding.passwordConfirmTextField.error = null
                binding.passwordConfirmTextField.isErrorEnabled = false
            }
        }

        binding.registerButton.setOnClickListener {
            val fields = viewListOf(
                binding.emailEditText,
                binding.passwordEditText,
                binding.loginButton,
                binding.registerButton
            )
            fields.clearFocus()
            fields.disable()

            val email = binding.emailEditText.text.toString()
            val password = binding.passwordEditText.text.toString()
            val displayName = binding.displayNameEditText.text.toString()
            val passwordConfirm = binding.passwordConfirmEditText.text.toString()

            when {
                displayName.isBlank() ->
                    showError(
                        binding.displayNameTextField,
                        R.string.register_error_display_name_required
                    )
                password != passwordConfirm ->
                    showError(
                        binding.passwordConfirmTextField,
                        R.string.register_error_passwords_not_match
                    )
                else ->
                    Firebase.auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener {
                            requireActivity().finishActivity(LOGGED_IN_REQUEST_CODE)
                        }
                        .addOnFailureListener { exception ->
                            Timber.w(exception, "Could not register user.")
                            when (exception) {
                                is FirebaseAuthWeakPasswordException ->
                                    showError(
                                        binding.passwordTextField,
                                        R.string.register_error_weak_password
                                    )
                                is FirebaseAuthInvalidCredentialsException ->
                                    showError(
                                        binding.emailTextField,
                                        R.string.register_error_invalid_email
                                    )
                                is FirebaseAuthUserCollisionException ->
                                    showError(
                                        binding.emailTextField,
                                        R.string.register_error_already_exists
                                    )
                                else -> toast(context, R.string.toast_error_internal)
                            }
                        }
                        .addOnCompleteListener {
                            fields.enable()
                        }
            }
        }
        binding.loginButton.setOnClickListener {
            (activity as? AuthActivity?)?.changePage(AuthActivity.PAGE_LOGIN)
        }
    }

    /**
     * Shows an error to the user through a text field.
     * @author Arnau Mora
     * @since 20210425
     * @param field The field to update
     * @param error The string resource of the message to show
     */
    private fun showError(field: TextInputLayout, @StringRes error: Int) {
        field.isErrorEnabled = true
        field.error = getString(error)
    }

    companion object {
        /**
         * Initializes a new instance of the [RegisterFragment].
         * @author Arnau Mora
         * @since 20210425
         */
        fun newInstance(): RegisterFragment = RegisterFragment()
    }
}
