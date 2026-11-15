package com.mipad.geckobrowser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

/**
 * Pure Canvas vector icon & rounded drawable kit so every toolbar/tab icon renders
 * razor-sharp on Xiaomi Mi Pad 1 (2048x1536, 320dpi, Android 4.4.4) with zero missing glyphs.
 */
public final class UiKit {
    public static final int ICON_BACK = 1;
    public static final int ICON_FORWARD = 2;
    public static final int ICON_RELOAD = 3;
    public static final int ICON_STOP = 4;
    public static final int ICON_HOME = 5;
    public static final int ICON_PLUS = 6;
    public static final int ICON_CLOSE = 7;
    public static final int ICON_LOCK = 8;
    public static final int ICON_GLOBE = 9;
    public static final int ICON_STAR_OUTLINE = 10;
    public static final int ICON_STAR_FILLED = 11;
    public static final int ICON_MENU = 12;
    public static final int ICON_SEARCH = 13;
    public static final int ICON_UP = 14;
    public static final int ICON_DOWN = 15;

    private UiKit() {}

    public static int dp(Context ctx, float dpVal) {
        return Math.round(dpVal * ctx.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable roundRect(int fillColor, float radiusPx, int strokeWidthPx, int strokeColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(fillColor);
        gd.setCornerRadius(radiusPx);
        if (strokeWidthPx > 0) {
            gd.setStroke(strokeWidthPx, strokeColor);
        }
        return gd;
    }

    public static GradientDrawable topRoundTab(int fillColor, float radiusPx, int strokeWidthPx, int strokeColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(fillColor);
        gd.setCornerRadii(new float[]{radiusPx, radiusPx, radiusPx, radiusPx, 0, 0, 0, 0});
        if (strokeWidthPx > 0) {
            gd.setStroke(strokeWidthPx, strokeColor);
        }
        return gd;
    }

    public static StateListDrawable pressableRound(int normalColor, int pressedColor, float radiusPx) {
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, roundRect(pressedColor, radiusPx, 0, 0));
        sld.addState(new int[]{}, roundRect(normalColor, radiusPx, 0, 0));
        return sld;
    }

    public static Drawable icon(Context ctx, final int type, final int color, float sizeDp) {
        final int sizePx = dp(ctx, sizeDp);
        final float stroke = Math.max(2f, ctx.getResources().getDisplayMetrics().density * 1.9f);
        return new Drawable() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            {
                paint.setColor(color);
                paint.setStrokeWidth(stroke);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
            }

            @Override
            public int getIntrinsicWidth() {
                return sizePx;
            }

            @Override
            public int getIntrinsicHeight() {
                return sizePx;
            }

            @Override
            public void draw(Canvas c) {
                Rect b = getBounds();
                float w = b.width() > 0 ? b.width() : sizePx;
                float h = b.height() > 0 ? b.height() : sizePx;
                float cx = b.left + w * 0.5f;
                float cy = b.top + h * 0.5f;
                float r = Math.min(w, h) * 0.34f;

                switch (type) {
                    case ICON_BACK: {
                        paint.setStyle(Paint.Style.STROKE);
                        Path p = new Path();
                        p.moveTo(cx + r * 0.2f, cy - r * 0.85f);
                        p.lineTo(cx - r * 0.65f, cy);
                        p.lineTo(cx + r * 0.2f, cy + r * 0.85f);
                        c.drawPath(p, paint);
                        c.drawLine(cx - r * 0.55f, cy, cx + r * 0.85f, cy, paint);
                        break;
                    }
                    case ICON_FORWARD: {
                        paint.setStyle(Paint.Style.STROKE);
                        Path p = new Path();
                        p.moveTo(cx - r * 0.2f, cy - r * 0.85f);
                        p.lineTo(cx + r * 0.65f, cy);
                        p.lineTo(cx - r * 0.2f, cy + r * 0.85f);
                        c.drawPath(p, paint);
                        c.drawLine(cx - r * 0.85f, cy, cx + r * 0.55f, cy, paint);
                        break;
                    }
                    case ICON_RELOAD: {
                        paint.setStyle(Paint.Style.STROKE);
                        RectF arc = new RectF(cx - r * 0.85f, cy - r * 0.85f, cx + r * 0.85f, cy + r * 0.85f);
                        c.drawArc(arc, 35f, 285f, false, paint);
                        Path arrow = new Path();
                        float ax = cx + r * 0.62f;
                        float ay = cy - r * 0.58f;
                        arrow.moveTo(ax - r * 0.45f, ay);
                        arrow.lineTo(ax + r * 0.12f, ay + r * 0.08f);
                        arrow.lineTo(ax + r * 0.05f, ay - r * 0.48f);
                        c.drawPath(arrow, paint);
                        break;
                    }
                    case ICON_STOP:
                    case ICON_CLOSE: {
                        paint.setStyle(Paint.Style.STROKE);
                        float d = r * 0.68f;
                        c.drawLine(cx - d, cy - d, cx + d, cy + d, paint);
                        c.drawLine(cx + d, cy - d, cx - d, cy + d, paint);
                        break;
                    }
                    case ICON_PLUS: {
                        paint.setStyle(Paint.Style.STROKE);
                        float d = r * 0.78f;
                        c.drawLine(cx - d, cy, cx + d, cy, paint);
                        c.drawLine(cx, cy - d, cx, cy + d, paint);
                        break;
                    }
                    case ICON_HOME: {
                        paint.setStyle(Paint.Style.STROKE);
                        Path roof = new Path();
                        roof.moveTo(cx - r * 0.95f, cy - r * 0.05f);
                        roof.lineTo(cx, cy - r * 0.9f);
                        roof.lineTo(cx + r * 0.95f, cy - r * 0.05f);
                        c.drawPath(roof, paint);
                        RectF body = new RectF(cx - r * 0.65f, cy - r * 0.15f, cx + r * 0.65f, cy + r * 0.85f);
                        c.drawLine(body.left, body.top, body.left, body.bottom, paint);
                        c.drawLine(body.right, body.top, body.right, body.bottom, paint);
                        c.drawLine(body.left, body.bottom, body.right, body.bottom, paint);
                        break;
                    }
                    case ICON_LOCK: {
                        paint.setStyle(Paint.Style.STROKE);
                        RectF shackle = new RectF(cx - r * 0.48f, cy - r * 0.9f, cx + r * 0.48f, cy + r * 0.05f);
                        c.drawArc(shackle, 180f, 180f, false, paint);
                        RectF box = new RectF(cx - r * 0.72f, cy - r * 0.1f, cx + r * 0.72f, cy + r * 0.88f);
                        c.drawRoundRect(box, stroke, stroke, paint);
                        break;
                    }
                    case ICON_GLOBE: {
                        paint.setStyle(Paint.Style.STROKE);
                        c.drawCircle(cx, cy, r * 0.88f, paint);
                        c.drawLine(cx - r * 0.85f, cy, cx + r * 0.85f, cy, paint);
                        RectF meridian = new RectF(cx - r * 0.44f, cy - r * 0.88f, cx + r * 0.44f, cy + r * 0.88f);
                        c.drawOval(meridian, paint);
                        break;
                    }
                    case ICON_STAR_OUTLINE:
                    case ICON_STAR_FILLED: {
                        paint.setStyle(type == ICON_STAR_FILLED ? Paint.Style.FILL_AND_STROKE : Paint.Style.STROKE);
                        Path star = new Path();
                        float outer = r * 0.92f;
                        float inner = r * 0.42f;
                        for (int i = 0; i < 10; i++) {
                            double ang = Math.toRadians(-90 + i * 36);
                            float rad = (i % 2 == 0) ? outer : inner;
                            float px = cx + (float) (Math.cos(ang) * rad);
                            float py = cy + (float) (Math.sin(ang) * rad);
                            if (i == 0) star.moveTo(px, py);
                            else star.lineTo(px, py);
                        }
                        star.close();
                        c.drawPath(star, paint);
                        break;
                    }
                    case ICON_MENU: {
                        paint.setStyle(Paint.Style.FILL);
                        float dotR = Math.max(2.2f, stroke * 0.72f);
                        c.drawCircle(cx, cy - r * 0.7f, dotR, paint);
                        c.drawCircle(cx, cy, dotR, paint);
                        c.drawCircle(cx, cy + r * 0.7f, dotR, paint);
                        break;
                    }
                    case ICON_SEARCH: {
                        paint.setStyle(Paint.Style.STROKE);
                        float sr = r * 0.62f;
                        c.drawCircle(cx - r * 0.15f, cy - r * 0.15f, sr, paint);
                        c.drawLine(cx + sr * 0.55f, cy + sr * 0.55f, cx + r * 0.85f, cy + r * 0.85f, paint);
                        break;
                    }
                    case ICON_UP: {
                        paint.setStyle(Paint.Style.STROKE);
                        Path p = new Path();
                        p.moveTo(cx - r * 0.75f, cy + r * 0.35f);
                        p.lineTo(cx, cy - r * 0.4f);
                        p.lineTo(cx + r * 0.75f, cy + r * 0.35f);
                        c.drawPath(p, paint);
                        break;
                    }
                    case ICON_DOWN: {
                        paint.setStyle(Paint.Style.STROKE);
                        Path p = new Path();
                        p.moveTo(cx - r * 0.75f, cy - r * 0.35f);
                        p.lineTo(cx, cy + r * 0.4f);
                        p.lineTo(cx + r * 0.75f, cy - r * 0.35f);
                        c.drawPath(p, paint);
                        break;
                    }
                }
            }

            @Override
            public void setAlpha(int alpha) {
                paint.setAlpha(alpha);
            }

            @Override
            public void setColorFilter(ColorFilter colorFilter) {
                paint.setColorFilter(colorFilter);
            }

            @Override
            public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        };
    }
}
