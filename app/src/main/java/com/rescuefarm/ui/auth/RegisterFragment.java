package com.rescuefarm.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.snackbar.Snackbar;
import com.rescuefarm.R;
import com.rescuefarm.domain.enums.CustomerType;
import com.rescuefarm.domain.enums.UserRole;

public class RegisterFragment extends Fragment {
    private AuthViewModel viewModel;
    private ProgressBar progressBar;
    private UserRole selectedRole;
    private EditText companyNameInput;
    private EditText taxCodeInput;
    private View businessFields;
    private MaterialButtonToggleGroup customerTypeGroup;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_register, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(
                requireActivity(), new AuthViewModelFactory(requireContext())
        ).get(AuthViewModel.class);
        progressBar = view.findViewById(R.id.authProgress);
        selectedRole = readSelectedRole();

        TextView title = view.findViewById(R.id.registerTitle);
        title.setText(selectedRole == UserRole.SELLER
                ? R.string.register_seller_title : R.string.register_customer_title);
        customerTypeGroup = view.findViewById(R.id.customerTypeGroup);
        businessFields = view.findViewById(R.id.businessFields);
        companyNameInput = view.findViewById(R.id.companyNameInput);
        taxCodeInput = view.findViewById(R.id.taxCodeInput);
        View sellerNotice = view.findViewById(R.id.sellerRegistrationNotice);

        boolean isCustomer = selectedRole == UserRole.CUSTOMER;
        customerTypeGroup.setVisibility(isCustomer ? View.VISIBLE : View.GONE);
        sellerNotice.setVisibility(isCustomer ? View.GONE : View.VISIBLE);
        updateCustomerTypeFields(view, customerTypeGroup.getCheckedButtonId());
        customerTypeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) updateCustomerTypeFields(view, checkedId);
        });

        EditText fullNameInput = view.findViewById(R.id.fullNameInput);
        EditText emailInput = view.findViewById(R.id.emailInput);
        EditText phoneInput = view.findViewById(R.id.phoneInput);
        EditText passwordInput = view.findViewById(R.id.passwordInput);

        view.findViewById(R.id.createAccountButton).setOnClickListener(unused -> {
            CustomerType customerType = selectedRole == UserRole.CUSTOMER
                    && customerTypeGroup.getCheckedButtonId() == R.id.businessTypeButton
                    ? CustomerType.BUSINESS : CustomerType.INDIVIDUAL;
            viewModel.register(
                    textOf(emailInput),
                    textOf(passwordInput),
                    textOf(fullNameInput),
                    textOf(phoneInput),
                    selectedRole,
                    customerType,
                    textOf(companyNameInput),
                    textOf(taxCodeInput)
            );
        });
        view.findViewById(R.id.backToLoginButton).setOnClickListener(unused ->
                NavHostFragment.findNavController(this).navigateUp()
        );
        viewModel.getScreenState().observe(getViewLifecycleOwner(), state -> renderState(view, state));
    }

    private UserRole readSelectedRole() {
        String roleName = getArguments() == null ? null : getArguments().getString("role");
        if (UserRole.SELLER.name().equals(roleName)) return UserRole.SELLER;
        return UserRole.CUSTOMER;
    }

    private void updateCustomerTypeFields(View view, int checkedId) {
        boolean isBusiness = selectedRole == UserRole.CUSTOMER
                && checkedId == R.id.businessTypeButton;
        businessFields.setVisibility(isBusiness ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.createAccountButton).setContentDescription(
                isBusiness ? getString(R.string.register_business_action)
                        : getString(R.string.create_account_action)
        );
    }

    private void renderState(View view, AuthScreenState state) {
        progressBar.setVisibility(
                state.getStatus() == AuthScreenState.Status.LOADING ? View.VISIBLE : View.GONE
        );
        if (state.getStatus() == AuthScreenState.Status.ERROR && state.getMessage() != null) {
            Snackbar.make(view, state.getMessage(), Snackbar.LENGTH_LONG).show();
            viewModel.clearTransientState();
        } else if (state.getStatus() == AuthScreenState.Status.AUTHENTICATED) {
            viewModel.clearTransientState();
            int destination = state.getUser() != null
                    && state.getUser().getRole() == UserRole.SELLER
                    ? R.id.sellerApplicationFragment
                    : R.id.action_registerFragment_to_guestHomeFragment;
            NavHostFragment.findNavController(this).navigate(destination);
        }
    }

    private static String textOf(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString();
    }
}
