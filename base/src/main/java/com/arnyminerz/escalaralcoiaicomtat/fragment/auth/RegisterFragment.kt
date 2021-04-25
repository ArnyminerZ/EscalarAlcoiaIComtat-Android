package com.arnyminerz.escalaralcoiaicomtat.fragment.auth

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import com.arnyminerz.escalaralcoiaicomtat.R
import com.arnyminerz.escalaralcoiaicomtat.activity.profile.AuthActivity
import com.arnyminerz.escalaralcoiaicomtat.databinding.FragmentAuthRegisterBinding
import com.arnyminerz.escalaralcoiaicomtat.fragment.preferences.PREF_WAITING_EMAIL_CONFIRMATION
import com.arnyminerz.escalaralcoiaicomtat.generic.WEBP_LOSSY_LEGACY
import com.arnyminerz.escalaralcoiaicomtat.generic.finishActivityWithResult
import com.arnyminerz.escalaralcoiaicomtat.generic.toast
import com.arnyminerz.escalaralcoiaicomtat.list.viewListOf
import com.arnyminerz.escalaralcoiaicomtat.shared.PROFILE_IMAGE_COMPRESSION_QUALITY
import com.arnyminerz.escalaralcoiaicomtat.shared.RESULT_CODE_WAITING_EMAIL_CONFIRMATION
import com.arnyminerz.escalaralcoiaicomtat.shared.exception_handler.handleStorageException
import com.arnyminerz.escalaralcoiaicomtat.view.visibility
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.ktx.auth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.ktx.storage
import timber.log.Timber
import java.io.ByteArrayOutputStream

class RegisterFragment private constructor() : Fragment() {
    private var _binding: FragmentAuthRegisterBinding? = null

    private val binding: FragmentAuthRegisterBinding = _binding!!

    /**
     * Specifies all the fields of the register form
     */
    private val fields
        get() = viewListOf(
            binding.emailEditText,
            binding.passwordEditText,
            binding.loginButton,
            binding.registerButton
        )

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
        binding.progressIndicator.visibility(false)

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
                else -> {
                    binding.progressIndicator.visibility(false)
                    binding.progressIndicator.isIndeterminate = true
                    binding.progressIndicator.visibility(true)

                    Firebase.auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener { result ->
                            uploadProfileImage(result)
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
                            fields.enable()
                            binding.progressIndicator.visibility(false)
                        }
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

    /**
     * Uploads the user's default profile image.
     * @author Arnau Mora
     * @since 20210424
     * @param result The [AuthResult] of the just created user.
     */
    @UiThread
    private fun uploadProfileImage(result: AuthResult) {
        binding.progressIndicator.visibility(false)
        binding.progressIndicator.isIndeterminate = false
        binding.progressIndicator.visibility(true)

        Timber.v("Registration has been successful, setting default profile image...")
        result.user?.let { user ->
            Timber.v("Starting profile image upload...")
            val storageRef = Firebase.storage.reference
            val profileImageRef = storageRef.child("profile/${user.uid}.webp")
            val profileImage = BitmapFactory.decodeResource(
                requireContext().resources,
                R.drawable.ic_profile_image
            )
            val baos = ByteArrayOutputStream()
            profileImage.compress(WEBP_LOSSY_LEGACY, PROFILE_IMAGE_COMPRESSION_QUALITY, baos)
            val data = baos.toByteArray()

            Timber.v("Uploading profile image...")
            profileImageRef.putBytes(data)
                .addOnProgressListener { task ->
                    binding.progressIndicator.progress = task.bytesTransferred.toInt()
                    binding.progressIndicator.max = task.totalByteCount.toInt()
                }
                .addOnSuccessListener {
                    binding.progressIndicator.visibility(false)
                    binding.progressIndicator.isIndeterminate = true
                    binding.progressIndicator.visibility(true)

                    updateProfileData(
                        result,
                        "gs://escalaralcoiaicomtat.appspot.com/" + profileImageRef.path
                    )
                }
                .addOnFailureListener {
                    Timber.e(it, "Could not upload profile image")
                    val e = handleStorageException(it as StorageException)
                        ?: return@addOnFailureListener
                    toast(requireContext(), e.first)
                    binding.progressIndicator.visibility(false)
                    fields.enable()
                }
        }
    }

    /**
     * Updates the profile image address after the user has been created, and the image uploaded.
     * Also changes the user's display name to the contents of
     * [FragmentAuthRegisterBinding.displayNameEditText].
     * @author Arnau Mora
     * @since 20210425
     * @param result The result that the user creation has given,
     * @param imageDownloadUrl The uploaded image url.
     */
    @UiThread
    private fun updateProfileData(result: AuthResult, imageDownloadUrl: String) {
        result.user?.updateProfile(
            userProfileChangeRequest {
                photoUri = Uri.parse(imageDownloadUrl)
                displayName = binding.displayNameEditText.text.toString()
            }
        )
            ?.addOnSuccessListener {
                PREF_WAITING_EMAIL_CONFIRMATION.put(true)
                activity.finishActivityWithResult(RESULT_CODE_WAITING_EMAIL_CONFIRMATION, null)
            }
            ?.addOnFailureListener {
                // TODO: Handle individual exceptions
                toast(R.string.toast_error_internal)
            }
            ?.addOnCompleteListener {
                binding.progressIndicator.visibility(false)
            }
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
