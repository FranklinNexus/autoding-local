package org.autoding.ocrprobe;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/** Manual, offline experiment. No attendance actions or location modification. */
public final class MainActivity extends Activity {
    private static final int OPEN_IMAGE = 41;
    private static final int[][] CROPS = {{44,590,518,730},{506,590,806,730},{310,1320,780,1605}};
    private TextView output;
    private Button open, sample;
    private TextRecognizer recognizer;
    private boolean busy;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL); column.setPadding(30,30,30,30);
        ScrollView scroll = new ScrollView(this); scroll.addView(column); setContentView(scroll);
        TextView description = new TextView(this); description.setTextSize(19);
        description.setText("AutoDing 本机 OCR 研究\n内置中文模型；本应用未申请网络权限。\n只识别三块考勤区域，不保存图片或原始文字，不向 AutoDing 回传记录。\n参考布局：1080×2400 华为竖屏及同宽高比缩放。其他布局保持待核实。");
        column.addView(description);
        open = new Button(this); open.setText("选择一张本机图片"); column.addView(open);
        sample = new Button(this); sample.setText("运行内置合成样例（可断网测试）"); column.addView(sample);
        output = new TextView(this); output.setTextSize(17); column.addView(output);
        recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        open.setOnClickListener(v -> {
            if (busy) return;
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
            startActivityForResult(intent, OPEN_IMAGE);
        });
        sample.setOnClickListener(v -> { if (!busy) recognize(synthetic(), ZonedDateTime.parse("2026-11-05T18:25:00+08:00"), true); });
        // Explicit laboratory entry point for a synthetic fixture only. It
        // never opens another app or turns the result into attendance data.
        if (getIntent().getBooleanExtra("run_fixture",false)) sample.post(() -> sample.performClick());
    }
    @Override public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if (request != OPEN_IMAGE || result != RESULT_OK || data == null || data.getData() == null || busy) return;
        Uri uri = data.getData();
        try {
            Bitmap bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), uri), (decoder, info, source) -> {
                if (info.getSize().getWidth() > 5000 || info.getSize().getHeight() > 5000) throw new IllegalArgumentException("image-too-large");
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
            recognize(bitmap, ZonedDateTime.now(ZoneId.systemDefault()), false);
        } catch (Exception failure) { output.setText("图片读取失败或尺寸超出研究范围；未保存输入。" ); }
    }
    private void setBusy(boolean value) { busy = value; open.setEnabled(!value); sample.setEnabled(!value); }
    private void recognize(Bitmap bitmap, ZonedDateTime now, boolean synthetic) {
        if (Math.abs(bitmap.getWidth()/(double)bitmap.getHeight() - 1080.0/2400.0) > 0.005) {
            bitmap.recycle(); output.setText("unsupported-layout：图片宽高比与参考布局不同。"); return;
        }
        setBusy(true); output.setText("本机处理中…");
        List<Bitmap> crops = new ArrayList<>(); List<Task<Text>> tasks = new ArrayList<>();
        try {
            for (int[] box : CROPS) {
                int left=(int)Math.floor(box[0]*bitmap.getWidth()/1080.0), top=(int)Math.floor(box[1]*bitmap.getHeight()/2400.0);
                int right=(int)Math.ceil(box[2]*bitmap.getWidth()/1080.0), bottom=(int)Math.ceil(box[3]*bitmap.getHeight()/2400.0);
                Bitmap crop=Bitmap.createBitmap(bitmap,left,top,right-left,bottom-top); crops.add(crop);
                tasks.add(recognizer.process(InputImage.fromBitmap(crop,0)));
            }
            Tasks.whenAllComplete(tasks).addOnCompleteListener(done -> {
                try {
                    boolean success=true; for (Task<Text> task : tasks) success &= task.isSuccessful();
                    if (!isDestroyed()) {
                        if (success) {
                            ReceiptParser.Result parsed=ReceiptParser.parse(tasks.get(0).getResult().getText(),tasks.get(1).getResult().getText(),tasks.get(2).getResult().getText(),now);
                            output.setText((synthetic ? "合成样例；固定测试时钟。\n" : "用户选图；当前手机时钟。\n") + parsed.display());
                        } else output.setText("offline-ocr-failed：模型或图像处理未就绪；未保存原始识别内容。");
                    }
                } finally {
                    for (Bitmap crop:crops) if (!crop.isRecycled()) crop.recycle();
                    if (!bitmap.isRecycled()) bitmap.recycle();
                    setBusy(false); if (isDestroyed()) recognizer.close();
                }
            });
        } catch (RuntimeException failure) {
            // Never recycle buffers while already-issued ML tasks may read them.
            Tasks.whenAllComplete(tasks).addOnCompleteListener(done -> {
                for (Bitmap crop:crops) if (!crop.isRecycled()) crop.recycle();
                if (!bitmap.isRecycled()) bitmap.recycle();
                if (!isDestroyed()) output.setText("offline-ocr-setup-failed：本机识别未完成。");
                setBusy(false); if (isDestroyed()) recognizer.close();
            });
        }
    }
    private Bitmap synthetic() {
        Bitmap image=Bitmap.createBitmap(1080,2400,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(image); canvas.drawColor(Color.WHITE);
        Paint text=new Paint(Paint.ANTI_ALIAS_FLAG); text.setColor(Color.BLACK); text.setTextSize(38);
        canvas.drawText("上班09:00",70,640,text); canvas.drawText("09:18已打卡",70,706,text);
        canvas.drawText("下班18:18",530,640,text); canvas.drawText("18:22已打卡",530,706,text);
        canvas.drawText("下班打卡",380,1380,text); canvas.drawText("11月5日周四",355,1480,text); canvas.drawText("18:25:00",390,1570,text);
        return image;
    }
    @Override protected void onDestroy() {
        super.onDestroy(); if (!busy && recognizer != null) recognizer.close();
    }
}
