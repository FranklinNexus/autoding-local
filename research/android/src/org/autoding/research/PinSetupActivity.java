package org.autoding.research;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Arrays;

/** The sole UI for entering a PIN; this activity is not exported. */
public final class PinSetupActivity extends Activity {
    private EditText first, second;
    private TextView status, message;
    @Override public void onCreate(Bundle state) {
        super.onCreate(null);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL); body.setPadding(32, 36, 32, 24);
        body.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        body.setSaveEnabled(false); setContentView(body);
        TextView heading = new TextView(this); heading.setText("本机解锁 PIN"); heading.setTextSize(24); body.addView(heading);
        TextView detail = new TextView(this);
        detail.setText("请输入本机 4–12 位数字 PIN。密文仅保存在此 App 的本机私有空间，供已启用的原生解锁流程使用。重新保存或清除会重置单次尝试保护。");
        body.addView(detail);
        first = field("输入 PIN"); second = field("再次输入 PIN"); body.addView(first); body.addView(second);
        status = new TextView(this); body.addView(status);
        message = new TextView(this); body.addView(message);
        Button save = new Button(this); save.setText("加密保存"); body.addView(save); save.setOnClickListener(v -> save());
        Button clear = new Button(this); clear.setText("清除已保存 PIN"); body.addView(clear);
        clear.setOnClickListener(v -> {
            clearFields();
            try { PinStore.clear(this); message.setText("已清除"); }
            catch (Exception ignored) { message.setText("清除失败，请重试"); }
            refresh();
        });
        refresh();
    }
    private EditText field(String hint) {
        EditText field = new EditText(this); field.setHint(hint);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        field.setTransformationMethod(PasswordTransformationMethod.getInstance());
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
        field.setSaveEnabled(false); field.setSaveFromParentEnabled(false);
        field.setLongClickable(false); field.setTextIsSelectable(false);
        field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        field.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_DONE);
        return field;
    }
    private static char[] chars(EditText field) {
        char[] value = new char[field.length()];
        field.getText().getChars(0, value.length, value, 0);
        return value;
    }
    private void save() {
        char[] a = chars(first), b = chars(second);
        try {
            if (!PinStore.valid(a)) { message.setText("请输入 4–12 位数字 PIN"); return; }
            if (!Arrays.equals(a, b)) { message.setText("两次输入不一致"); return; }
            PinStore.save(this, a); message.setText("已加密保存");
        } catch (Exception ignored) { message.setText("保存失败，请重试"); }
        finally { Arrays.fill(a, '\0'); Arrays.fill(b, '\0'); clearFields(); refresh(); }
    }
    private void refresh() { status.setText(PinStore.isSaved(this) ? "PIN 状态：已保存" : "PIN 状态：未保存"); }
    private void clearFields() { if (first != null) first.getText().clear(); if (second != null) second.getText().clear(); }
    @Override protected void onSaveInstanceState(Bundle state) { /* Never save input or hierarchy state. */ }
    @Override protected void onPause() { clearFields(); super.onPause(); }
    @Override protected void onDestroy() { clearFields(); super.onDestroy(); }
}
