package org.autoding.research;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/** Offline receipt reader. Ownership of the input bitmap transfers to this call. */
public final class FrameOcr {
    private FrameOcr() {}
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int[][] CROPS = {{44,590,518,730},{506,590,840,730},{310,1320,780,1605}};
    // Reference-layout icon pixels only. Receipt text begins at x173/x618.
    private static final int[][] ICON_MASKS = {{44,675,166,730},{506,675,612,730}};
    // Isolate context labels. The button model gets the observed full sheet,
    // then only real OCR boxes inside EARLY_BUTTON_BAND enter the parser.
    private static final int[][] EARLY_CROPS = {{40,1790,400,1980},{40,1985,400,2100},
        {40,2090,420,2160},{0,1750,1080,2400}};
    private static final int[] EARLY_BUTTON_BAND = {40,2220,1040,2360};
    private static final long MAX_FRAME_AGE_MS = 60000;

    public interface Callback { void onResult(Result result); }
    public interface EarlyCheckoutCallback { void onResult(EarlyCheckoutResult result); }
    public interface SuccessCallback { void onResult(CheckoutSuccessResult result); }

    public static final class CheckoutSuccessResult {
        public final boolean accepted;
        public final String reason,date,checkOut;
        public final Rect actionBounds;
        public final long freshTimeMs;
        public final JSONObject diagnostics;
        private CheckoutSuccessResult(boolean accepted,String reason,String date,String checkOut,Rect actionBounds,long freshTimeMs,JSONObject diagnostics) {
            this.accepted=accepted; this.reason=reason; this.date=accepted ? date : null; this.checkOut=accepted ? checkOut : null;
            this.actionBounds=actionBounds == null ? null : new Rect(actionBounds); this.freshTimeMs=freshTimeMs; this.diagnostics=diagnostics;
        }
    }

    public static final class EarlyCheckoutResult {
        public final boolean accepted;
        public final String reason,label;
        public final Rect bounds;
        public final long freshTimeMs;
        public final JSONObject diagnostics;
        private EarlyCheckoutResult(boolean accepted,String reason,Rect bounds,long freshTimeMs,JSONObject diagnostics) {
            this.accepted=accepted; this.reason=reason; this.label=accepted ? "早退打卡" : null;
            this.bounds=bounds == null ? null : new Rect(bounds); this.freshTimeMs=freshTimeMs;
            this.diagnostics=diagnostics;
        }
    }

    public static final class Result {
        public final boolean accepted;
        public final String reason, date, checkIn, checkOut;
        public final String actionLabel;
        public final Rect actionBounds;
        public final JSONObject diagnostics;
        public final long freshTimeMs;
        private Result(boolean accepted, String reason, String date, String checkIn, String checkOut, long freshTimeMs,
                       String actionLabel, Rect actionBounds, JSONObject diagnostics) {
            this.accepted=accepted; this.reason=reason; this.date=date;
            this.checkIn=checkIn; this.checkOut=checkOut; this.freshTimeMs=freshTimeMs;
            this.actionLabel=actionLabel; this.actionBounds=actionBounds == null ? null : new Rect(actionBounds);
            this.diagnostics=diagnostics;
        }
    }

    /**
     * Reads the three established Huawei portrait regions, never the full page.
     * freshTimeMs is the wall-clock epoch time at screenshot capture, not uptime.
     * Exactly one main-thread callback is delivered. Raw text/images are not stored.
     */
    public static void recognize(Bitmap bitmap, long freshTimeMs, Callback callback) {
        if (callback == null) { release(bitmap); throw new IllegalArgumentException("callback-required"); }
        if (bitmap == null || bitmap.isRecycled()) {
            fail(bitmap,freshTimeMs,"frame-missing",callback); return;
        }
        long age=System.currentTimeMillis()-freshTimeMs;
        if (freshTimeMs <= 0 || age < -2000 || age > MAX_FRAME_AGE_MS) {
            fail(bitmap,freshTimeMs,"frame-not-fresh",callback); return;
        }
        int width=bitmap.getWidth(), height=bitmap.getHeight();
        if (width < 540 || height < 1200 || width > 5000 || height > 5000 ||
            Math.abs(width/(double)height-1080.0/2400.0) > 0.005) {
            fail(bitmap,freshTimeMs,"unsupported-layout",callback); return;
        }
        final ZonedDateTime frameTime;
        try { frameTime=Instant.ofEpochMilli(freshTimeMs).atZone(ZoneId.systemDefault()); }
        catch (RuntimeException exception) { fail(bitmap,freshTimeMs,"frame-time-invalid",callback); return; }

        List<Bitmap> crops=new ArrayList<>();
        List<Task<Text>> tasks=new ArrayList<>();
        final TextRecognizer recognizer;
        try { recognizer=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build()); }
        catch (RuntimeException exception) { fail(bitmap,freshTimeMs,"ocr-model-unavailable",callback); return; }
        String setupReason=null;
        try {
            for (int index=0;index<CROPS.length;index++) {
                int[] box=CROPS[index];
                int left=(int)Math.floor(box[0]*width/1080.0), top=(int)Math.floor(box[1]*height/2400.0);
                int right=(int)Math.ceil(box[2]*width/1080.0), bottom=(int)Math.ceil(box[3]*height/2400.0);
                Bitmap crop=Bitmap.createBitmap(bitmap,left,top,right-left,bottom-top);
                if (index < ICON_MASKS.length) {
                    Bitmap originalCrop=crop;
                    try { crop=originalCrop.copy(Bitmap.Config.ARGB_8888,true); }
                    finally { if (originalCrop != bitmap) release(originalCrop); }
                    if (crop == null) throw new IllegalStateException("mutable-crop-unavailable");
                }
                crops.add(crop);
                if (index < ICON_MASKS.length) {
                    int[] icon=ICON_MASKS[index];
                    Paint fill=new Paint(); fill.setColor(Color.rgb(245,245,245));
                    new Canvas(crop).drawRect(
                        (int)Math.floor(icon[0]*width/1080.0)-left,
                        (int)Math.floor(icon[1]*height/2400.0)-top,
                        (int)Math.ceil(icon[2]*width/1080.0)-left,
                        (int)Math.ceil(icon[3]*height/2400.0)-top,fill);
                }
                tasks.add(recognizer.process(InputImage.fromBitmap(crop,0)));
            }
            // Unpunched text starts where the tick icon would be. Independently
            // read both original cards; use them only for an exact empty schema.
            for (int index=0;index<2;index++) {
                int[] box=CROPS[index];
                int left=(int)Math.floor(box[0]*width/1080.0), top=(int)Math.floor(box[1]*height/2400.0);
                Bitmap crop=Bitmap.createBitmap(bitmap,left,top,(int)Math.ceil(box[2]*width/1080.0)-left,
                    (int)Math.ceil(box[3]*height/2400.0)-top);
                crops.add(crop); tasks.add(recognizer.process(InputImage.fromBitmap(crop,0)));
            }
        } catch (RuntimeException exception) { setupReason="ocr-setup-failed"; }
        final String failure=setupReason;
        // Even partial setup may have issued native readers. Recycle only once
        // every issued task completes, then close the per-frame recognizer.
        Tasks.whenAllComplete(tasks).addOnCompleteListener(done -> {
            Result result;
            try {
                boolean success=failure == null && tasks.size() == 5;
                for (Task<Text> task:tasks) success &= task.isSuccessful();
                if (!success) result=rejected(freshTimeMs,failure == null ? "offline-ocr-failed" : failure);
                else {
                    String morning=tasks.get(0).getResult().getText(), evening=tasks.get(1).getResult().getText(),
                        dateText=tasks.get(2).getResult().getText();
                    String rawMorning=tasks.get(3).getResult().getText(), rawEvening=tasks.get(4).getResult().getText();
                    if (ReceiptParser.exactUnpunched(rawMorning,"上班")) morning=rawMorning;
                    if (ReceiptParser.exactUnpunched(rawEvening,"下班")) evening=rawEvening;
                    JSONObject diagnostics=new JSONObject(ReceiptParser.diagnostics(morning,evening,dateText));
                    ReceiptParser.Result parsed=ReceiptParser.parse(morning,evening,dateText,frameTime);
                    String label=null; Rect bounds=null; int hits=0;
                    int dx=(int)Math.floor(CROPS[2][0]*width/1080.0), dy=(int)Math.floor(CROPS[2][1]*height/2400.0);
                    for (Text.TextBlock block:tasks.get(2).getResult().getTextBlocks()) {
                        for (Text.Line line:block.getLines()) {
                            String value=line.getText().replaceAll("\\s+", "");
                            if (!value.equals("下班打卡") && !value.equals("更新打卡") && !value.equals("上班打卡")) continue;
                            hits++; label=value;
                            Rect box=line.getBoundingBox();
                            bounds=box == null || box.isEmpty() ? null : new Rect(box);
                            if (bounds != null) bounds.offset(dx,dy);
                        }
                    }
                    if (hits != 1 || !parsed.accepted) { label=null; bounds=null; }
                    result=new Result(parsed.accepted,parsed.reason,parsed.date,parsed.checkIn,parsed.checkOut,
                        freshTimeMs,label,bounds,diagnostics);
                }
            } catch (RuntimeException exception) { result=rejected(freshTimeMs,"ocr-result-unconfirmed"); }
            finally {
                for (Bitmap crop:crops) release(crop);
                release(bitmap);
                recognizer.close();
            }
            deliver(callback,result);
        });
    }

    /** Reads only the observed bottom sheet. It never enters a reason or clicks. */
    public static void recognizeEarlyCheckout(Bitmap bitmap,long freshTimeMs,EarlyCheckoutCallback callback) {
        if (callback == null) { release(bitmap); throw new IllegalArgumentException("callback-required"); }
        if (bitmap == null || bitmap.isRecycled()) {
            release(bitmap); deliverEarly(callback,earlyRejected(freshTimeMs,"frame-missing")); return;
        }
        long age=System.currentTimeMillis()-freshTimeMs;
        if (freshTimeMs <= 0 || age < -2000 || age > MAX_FRAME_AGE_MS) {
            release(bitmap); deliverEarly(callback,earlyRejected(freshTimeMs,"frame-not-fresh")); return;
        }
        int width=bitmap.getWidth(),height=bitmap.getHeight();
        if (width < 540 || height < 1200 || width > 5000 || height > 5000 ||
            Math.abs(width/(double)height-1080.0/2400.0) > 0.005) {
            release(bitmap); deliverEarly(callback,earlyRejected(freshTimeMs,"unsupported-layout")); return;
        }
        final TextRecognizer recognizer;
        try { recognizer=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build()); }
        catch (RuntimeException exception) {
            release(bitmap); deliverEarly(callback,earlyRejected(freshTimeMs,"ocr-model-unavailable")); return;
        }
        List<Bitmap> crops=new ArrayList<>();
        List<Rect> regions=new ArrayList<>();
        List<Task<Text>> tasks=new ArrayList<>();
        String setupReason=null;
        try {
            for (int i=0;i<EARLY_CROPS.length;i++) {
                int[] source=EARLY_CROPS[i];
                Rect region=new Rect((int)Math.floor(source[0]*width/1080.0),(int)Math.floor(source[1]*height/2400.0),
                    (int)Math.ceil(source[2]*width/1080.0),(int)Math.ceil(source[3]*height/2400.0));
                Bitmap crop=Bitmap.createBitmap(bitmap,region.left,region.top,region.width(),region.height());
                if (i == 2) {
                    Bitmap original=crop;
                    try { crop=original.copy(Bitmap.Config.ARGB_8888,true); }
                    finally { if (original != bitmap) release(original); }
                    if (crop == null) throw new IllegalStateException("mutable-crop-unavailable");
                }
                crops.add(crop); regions.add(region);
                if (i == 2) enhancePlaceholderContrast(crop);
                tasks.add(recognizer.process(InputImage.fromBitmap(crop,0)));
            }
        } catch (RuntimeException exception) { setupReason="ocr-setup-failed"; }
        final String failure=setupReason;
        Tasks.whenAllComplete(tasks).addOnCompleteListener(done -> {
            EarlyCheckoutResult result;
            try {
                boolean success=failure == null && tasks.size() == 4;
                for (Task<Text> task:tasks) success &= task.isSuccessful();
                if (!success) result=earlyRejected(freshTimeMs,failure == null ? "offline-ocr-failed" : failure);
                else {
                    List<List<String>> bandTexts=new ArrayList<>();
                    List<List<Text.Line>> bandLines=new ArrayList<>();
                    int buttonContextLines=0;
                    Rect buttonBand=new Rect((int)Math.floor(EARLY_BUTTON_BAND[0]*width/1080.0),
                        (int)Math.floor(EARLY_BUTTON_BAND[1]*height/2400.0),
                        (int)Math.ceil(EARLY_BUTTON_BAND[2]*width/1080.0),
                        (int)Math.ceil(EARLY_BUTTON_BAND[3]*height/2400.0));
                    for (int i=0;i<tasks.size();i++) {
                        List<Text.Line> observed=new ArrayList<>();
                        for (Text.TextBlock block:tasks.get(i).getResult().getTextBlocks()) {
                            for (Text.Line line:block.getLines()) {
                                if (i == 3) {
                                    buttonContextLines++;
                                    Rect box=line.getBoundingBox();
                                    if (box == null || box.isEmpty()) continue;
                                    Rect screenBox=new Rect(box);
                                    screenBox.offset(regions.get(i).left,regions.get(i).top);
                                    if (!buttonBand.contains(screenBox)) continue;
                                }
                                observed.add(line);
                            }
                        }
                        List<String> texts=new ArrayList<>();
                        for (Text.Line line:observed) texts.add(line.getText());
                        bandTexts.add(texts); bandLines.add(observed);
                    }
                    EarlyCheckoutParser.Result parsed=EarlyCheckoutParser.parseBands(bandTexts);
                    JSONObject diagnostics=new JSONObject(parsed.diagnostics);
                    diagnostics.put("buttonContextLineCount",buttonContextLines);
                    diagnostics.put("buttonBandLineCount",bandLines.get(3).size());
                    Rect button=null; String reason=parsed.reason;
                    int validBounds=0, requiredBounds=0;
                    for (int i=0;i<bandLines.size();i++) {
                        Rect region=regions.get(i); boolean valid=!bandLines.get(i).isEmpty();
                        for (Text.Line line:bandLines.get(i)) {
                            Rect box=line.getBoundingBox();
                            valid &= box != null && !box.isEmpty() && box.left >= 0 && box.top >= 0 &&
                                box.right <= region.width() && box.bottom <= region.height();
                        }
                        if (valid) { validBounds++; if (i != 2) requiredBounds++; }
                    }
                    diagnostics.put("observedBandMatches",validBounds);
                    diagnostics.put("requiredBandBoundsMatches",requiredBounds);
                    if (parsed.accepted) {
                        if (requiredBounds != 3) reason="early-sheet-bounds-unconfirmed";
                        else {
                            // parseBands requires one exact button line. Keep its
                            // actual OCR box; the other regions only prove context.
                            button=new Rect(bandLines.get(3).get(0).getBoundingBox());
                            button.offset(regions.get(3).left,regions.get(3).top);
                        }
                    }
                    result=new EarlyCheckoutResult(reason.isEmpty(),reason,reason.isEmpty() ? button : null,freshTimeMs,diagnostics);
                }
            } catch (RuntimeException exception) { result=earlyRejected(freshTimeMs,"early-sheet-result-unconfirmed"); }
            catch (org.json.JSONException exception) { result=earlyRejected(freshTimeMs,"early-sheet-result-unconfirmed"); }
            finally { for (Bitmap crop:crops) release(crop); release(bitmap); recognizer.close(); }
            deliverEarly(callback,result);
        });
    }

    /** Reads only the observed success modal; never clicks its result action. */
    public static void recognizeCheckoutSuccess(Bitmap bitmap,long freshTimeMs,SuccessCallback callback) {
        recognizeSuccess(bitmap,freshTimeMs,false,callback);
    }
    public static void recognizeCheckInSuccess(Bitmap bitmap,long freshTimeMs,SuccessCallback callback) {
        recognizeSuccess(bitmap,freshTimeMs,true,callback);
    }
    private static void recognizeSuccess(Bitmap bitmap,long freshTimeMs,boolean morning,SuccessCallback callback) {
        final String expectedHeading=morning ? "上班打卡成功" : "下班打卡成功";
        if (callback == null) { release(bitmap); throw new IllegalArgumentException("callback-required"); }
        if (bitmap == null || bitmap.isRecycled()) {
            release(bitmap); deliverSuccess(callback,successRejected(freshTimeMs,"frame-missing")); return;
        }
        long age=System.currentTimeMillis()-freshTimeMs;
        if (freshTimeMs <= 0 || age < -2000 || age > MAX_FRAME_AGE_MS) {
            release(bitmap); deliverSuccess(callback,successRejected(freshTimeMs,"frame-not-fresh")); return;
        }
        int width=bitmap.getWidth(),height=bitmap.getHeight();
        if (width < 540 || height < 1200 || width > 5000 || height > 5000 ||
            Math.abs(width/(double)height-1080.0/2400.0) > 0.005) {
            release(bitmap); deliverSuccess(callback,successRejected(freshTimeMs,"unsupported-layout")); return;
        }
        final TextRecognizer recognizer;
        try { recognizer=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build()); }
        catch (RuntimeException exception) {
            release(bitmap); deliverSuccess(callback,successRejected(freshTimeMs,"ocr-model-unavailable")); return;
        }
        // Keep the complete modal as context; independently read the heading
        // without the icon, replacing only its single observed heading-band line.
        int[][] sources={{115,1750,967,2140},{420,1760,800,1860}};
        List<Bitmap> crops=new ArrayList<>();
        List<Rect> regions=new ArrayList<>();
        List<Task<Text>> tasks=new ArrayList<>();
        String setupReason=null;
        try {
            for (int i=0;i<sources.length;i++) {
                int[] source=sources[i];
                Rect region=new Rect((int)Math.floor(source[0]*width/1080.0),(int)Math.floor(source[1]*height/2400.0),
                    (int)Math.ceil(source[2]*width/1080.0),(int)Math.ceil(source[3]*height/2400.0));
                Bitmap crop=Bitmap.createBitmap(bitmap,region.left,region.top,region.width(),region.height());
                if (i == 0) {
                    Bitmap original=crop;
                    try { crop=original.copy(Bitmap.Config.ARGB_8888,true); }
                    finally { if (original != bitmap) release(original); }
                    if (crop == null) throw new IllegalStateException("mutable-crop-unavailable");
                    Paint fill=new Paint(); fill.setColor(Color.WHITE);
                    new Canvas(crop).drawRect((int)Math.floor(350*width/1080.0)-region.left,
                        (int)Math.floor(1770*height/2400.0)-region.top,
                        (int)Math.ceil(415*width/1080.0)-region.left,
                        (int)Math.ceil(1840*height/2400.0)-region.top,fill);
                }
                crops.add(crop); regions.add(region);
                tasks.add(recognizer.process(InputImage.fromBitmap(crop,0)));
            }
        } catch (RuntimeException exception) { setupReason="ocr-setup-failed"; }
        final String failure=setupReason;
        Tasks.whenAllComplete(tasks).addOnCompleteListener(done -> {
            CheckoutSuccessResult result;
            try {
                boolean successful=failure == null && tasks.size() == 2;
                for (Task<Text> task:tasks) successful &= task.isSuccessful();
                if (!successful) result=successRejected(freshTimeMs,failure == null ? "offline-ocr-failed" : failure);
                else {
                    List<Text.Line> observed=new ArrayList<>(),headingLines=new ArrayList<>();
                    for (Text.TextBlock block:tasks.get(0).getResult().getTextBlocks()) observed.addAll(block.getLines());
                    for (Text.TextBlock block:tasks.get(1).getResult().getTextBlocks()) headingLines.addAll(block.getLines());
                    observed.sort((a,b) -> Integer.compare(a.getBoundingBox() == null ? Integer.MAX_VALUE : a.getBoundingBox().top,
                        b.getBoundingBox() == null ? Integer.MAX_VALUE : b.getBoundingBox().top));
                    Rect headingBand=new Rect((int)Math.floor(300*width/1080.0),(int)Math.floor(1760*height/2400.0),
                        (int)Math.ceil(800*width/1080.0),(int)Math.ceil(1860*height/2400.0));
                    boolean independentHeading=false; Rect independentBox=null;
                    if (headingLines.size() == 1) {
                        Text.Line line=headingLines.get(0); Rect box=line.getBoundingBox();
                        if (box != null && !box.isEmpty()) {
                            independentBox=new Rect(box); independentBox.offset(regions.get(1).left,regions.get(1).top);
                            independentHeading=expectedHeading.equals(CheckoutSuccessParser.normalize(line.getText())) &&
                                headingBand.contains(independentBox) && regions.get(1).contains(independentBox);
                        }
                    }
                    List<String> texts=new ArrayList<>(); List<Rect> boxes=new ArrayList<>();
                    int contextHeadingCount=0;
                    for (Text.Line line:observed) {
                        Rect box=line.getBoundingBox(),screen=box == null ? null : new Rect(box);
                        if (screen != null) screen.offset(regions.get(0).left,regions.get(0).top);
                        boolean inHeading=screen != null && !screen.isEmpty() && headingBand.contains(screen);
                        if (inHeading) contextHeadingCount++;
                        texts.add(inHeading && independentHeading ? expectedHeading : line.getText());
                        boxes.add(inHeading && independentHeading ? independentBox : screen);
                    }
                    CheckoutSuccessParser.Result parsed=morning ? CheckoutSuccessParser.parseMorning(texts) : CheckoutSuccessParser.parse(texts);
                    JSONObject diagnostics=new JSONObject(parsed.diagnostics);
                    diagnostics.put("headingLineCount",headingLines.size()); diagnostics.put("independentHeadingExact",independentHeading);
                    diagnostics.put("contextHeadingLineCount",contextHeadingCount);
                    String reason=parsed.reason; Rect action=null; int validBounds=0;
                    if (!independentHeading || contextHeadingCount != 1) reason="success-modal-heading-band-unconfirmed";
                    else if (parsed.accepted) {
                        int[] indexes={parsed.headingIndex,parsed.timeIndex,parsed.actionIndex};
                        int[][] bands={{420,1760,800,1860},{200,1850,900,1950},{300,1990,800,2120}};
                        for (int i=0;i<indexes.length;i++) {
                            Rect screen=boxes.get(indexes[i]); int[] reference=bands[i];
                            Rect band=new Rect((int)Math.floor(reference[0]*width/1080.0),(int)Math.floor(reference[1]*height/2400.0),
                                (int)Math.ceil(reference[2]*width/1080.0),(int)Math.ceil(reference[3]*height/2400.0));
                            if (screen != null && !screen.isEmpty() && band.contains(screen)) { validBounds++; if (i == 2) action=screen; }
                        }
                        if (validBounds != 3 || action == null) reason="success-modal-bounds-unconfirmed";
                    }
                    diagnostics.put("observedBandMatches",validBounds);
                    result=new CheckoutSuccessResult(reason.isEmpty(),reason,parsed.date,parsed.checkOut,
                        reason.isEmpty() ? action : null,freshTimeMs,diagnostics);
                }
            } catch (RuntimeException exception) { result=successRejected(freshTimeMs,"success-modal-result-unconfirmed"); }
            catch (org.json.JSONException exception) { result=successRejected(freshTimeMs,"success-modal-result-unconfirmed"); }
            finally { for (Bitmap crop:crops) release(crop); release(bitmap); recognizer.close(); }
            deliverSuccess(callback,result);
        });
    }

    private static CheckoutSuccessResult successRejected(long time,String reason) {
        return new CheckoutSuccessResult(false,reason,null,null,null,time,new JSONObject());
    }
    private static void deliverSuccess(SuccessCallback callback,CheckoutSuccessResult result) {
        if (Looper.myLooper() == Looper.getMainLooper()) callback.onResult(result);
        else MAIN.post(() -> callback.onResult(result));
    }

    private static void enhancePlaceholderContrast(Bitmap crop) {
        int width=crop.getWidth(),height=crop.getHeight();
        int[] pixels=new int[width*height]; crop.getPixels(pixels,0,width,0,0,width,height);
        for (int i=0;i<pixels.length;i++) {
            int color=pixels[i];
            int gray=(Color.red(color)*299+Color.green(color)*587+Color.blue(color)*114)/1000;
            int contrast=Math.max(0,Math.min(255,(gray-200)*255/55));
            pixels[i]=Color.rgb(contrast,contrast,contrast);
        }
        crop.setPixels(pixels,0,width,0,0,width,height);
    }

    private static EarlyCheckoutResult earlyRejected(long time,String reason) {
        return new EarlyCheckoutResult(false,reason,null,time,new JSONObject());
    }
    private static void deliverEarly(EarlyCheckoutCallback callback,EarlyCheckoutResult result) {
        if (Looper.myLooper() == Looper.getMainLooper()) callback.onResult(result);
        else MAIN.post(() -> callback.onResult(result));
    }
    private static Result rejected(long freshTimeMs,String reason) { return new Result(false,reason,null,null,null,freshTimeMs,null,null,new JSONObject()); }
    private static void release(Bitmap bitmap) { if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle(); }
    private static void fail(Bitmap bitmap,long freshTimeMs,String reason,Callback callback) {
        release(bitmap); deliver(callback,rejected(freshTimeMs,reason));
    }
    private static void deliver(Callback callback,Result result) {
        if (Looper.myLooper() == Looper.getMainLooper()) callback.onResult(result);
        else MAIN.post(() -> callback.onResult(result));
    }
}
