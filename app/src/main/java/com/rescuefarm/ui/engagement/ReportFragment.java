package com.rescuefarm.ui.engagement;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.rescuefarm.R;
import com.rescuefarm.domain.enums.ReportTargetType;
public class ReportFragment extends Fragment {
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_report,parent,false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        Spinner type=view.findViewById(R.id.reportTargetType);
        EditText id=view.findViewById(R.id.reportTargetId), reason=view.findViewById(R.id.reportReason), description=view.findViewById(R.id.reportDescription);
        reason.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(120)});
        description.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1000)});
        LinearLayout form=(LinearLayout)id.getParent();
        Spinner parentType=new Spinner(requireContext());
        parentType.setAdapter(new ArrayAdapter<>(requireContext(),android.R.layout.simple_spinner_dropdown_item,new String[]{"Bình luận trong chiến dịch","Bình luận trong bài viết cũ"}));
        EditText parentId=new EditText(requireContext()); parentId.setHint("Mã chiến dịch hoặc bài viết chứa bình luận");
        form.addView(parentType,form.indexOfChild(id)); form.addView(parentId,form.indexOfChild(id));
        type.setAdapter(new ArrayAdapter<>(requireContext(),android.R.layout.simple_spinner_dropdown_item,ReportTargetType.values()));
        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent,View selected,int position,long rowId) {
                boolean comment=type.getSelectedItem()==ReportTargetType.COMMENT;
                parentType.setVisibility(comment?View.VISIBLE:View.GONE); parentId.setVisibility(comment?View.VISIBLE:View.GONE);
                id.setHint(comment?"Mã bình luận":getString(R.string.report_target_hint));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        Bundle args=getArguments();
        if(args!=null) {
            String target=args.getString("targetId",""); id.setText(target);
            try { type.setSelection(ReportTargetType.valueOf(args.getString("targetType","PRODUCT")).ordinal()); } catch(IllegalArgumentException ignored) { }
            String[] parts=target.split("/",-1);
            if("COMMENT".equals(args.getString("targetType")) && parts.length==4 && parts[2].equals("comments")
                    && (parts[0].equals("campaigns")||parts[0].equals("posts"))) {
                parentType.setSelection(parts[0].equals("campaigns")?0:1); parentId.setText(parts[1]); id.setText(parts[3]);
                type.setEnabled(false); parentType.setEnabled(false); parentId.setEnabled(false); id.setEnabled(false);
            }
        }
        EngagementViewModel vm=new ViewModelProvider(this,new EngagementViewModelFactory(requireContext())).get(EngagementViewModel.class);
        vm.getState().observe(getViewLifecycleOwner(), value -> {
            if(value.getStatus()==EngagementState.Status.ERROR||value.getStatus()==EngagementState.Status.SUCCESS)
                Toast.makeText(requireContext(),value.getMessage(),Toast.LENGTH_LONG).show();
        });
        // Public flow has a fixed target supplied by the content screen, never a user-entered ID.
        boolean contextual = args != null && !args.getString("targetId", "").trim().isEmpty();
        type.setVisibility(View.GONE); id.setVisibility(View.GONE); parentType.setVisibility(View.GONE); parentId.setVisibility(View.GONE);
        TextView context = new TextView(requireContext());
        String subject=type.getSelectedItem()==ReportTargetType.REVIEW?"đánh giá":type.getSelectedItem()==ReportTargetType.COMMENT?"bình luận"
                :type.getSelectedItem()==ReportTargetType.USER?"người bán":type.getSelectedItem()==ReportTargetType.CAMPAIGN?"chiến dịch":"nội dung";
        context.setText(contextual ? "Báo cáo "+subject+" này. Vui lòng cho biết lý do và thông tin giúp quản trị viên xem xét."
                : "Hãy chọn Báo cáo ngay tại đánh giá, bình luận hoặc hồ sơ người bán cần phản ánh.");
        form.addView(context, 0); view.findViewById(R.id.submitReportButton).setEnabled(contextual);
        type.setOnItemSelectedListener(null);
        view.findViewById(R.id.submitReportButton).setOnClickListener(v -> {
            String uid=FirebaseAuth.getInstance().getCurrentUser()==null?"":FirebaseAuth.getInstance().getCurrentUser().getUid();
            String target=id.getText().toString().trim(); ReportTargetType selected=(ReportTargetType)type.getSelectedItem();
            if(selected==ReportTargetType.COMMENT) target=(parentType.getSelectedItemPosition()==0?"campaigns/":"posts/")
                    +parentId.getText().toString().trim()+"/comments/"+target;
            vm.report(uid,selected,target,reason.getText().toString().trim(),description.getText().toString().trim());
        });
        com.google.android.material.button.MaterialButton history=new com.google.android.material.button.MaterialButton(
                requireContext(),null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
        history.setText("Báo cáo của tôi"); history.setOnClickListener(v -> androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(R.id.reportResultFragment));
        form.addView(history);
    }
}
