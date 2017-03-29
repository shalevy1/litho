/**
 * Copyright (c) 2014-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.facebook.litho.widget;

import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.support.v4.text.TextDirectionHeuristicCompat;
import android.support.v4.text.TextDirectionHeuristicsCompat;
import android.support.v4.util.Pools.SynchronizedPool;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.Layout;
import android.text.Layout.Alignment;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.text.style.ClickableSpan;
import android.util.Log;

import com.facebook.R;
import com.facebook.litho.ComponentContext;
import com.facebook.litho.ComponentLayout;
import com.facebook.litho.ComponentsLogger;
import com.facebook.litho.config.ComponentsConfiguration;
import com.facebook.litho.Output;
import com.facebook.litho.Size;
import com.facebook.litho.SizeSpec;
import com.facebook.litho.annotations.FromBoundsDefined;
import com.facebook.litho.annotations.FromMeasure;
import com.facebook.litho.annotations.GetExtraAccessibilityNodeAt;
import com.facebook.litho.annotations.GetExtraAccessibilityNodesCount;
import com.facebook.litho.annotations.MountSpec;
import com.facebook.litho.annotations.OnBoundsDefined;
import com.facebook.litho.annotations.OnCreateMountContent;
import com.facebook.litho.annotations.OnLoadStyle;
import com.facebook.litho.annotations.OnMeasure;
import com.facebook.litho.annotations.OnMount;
import com.facebook.litho.annotations.OnPopulateAccessibilityNode;
import com.facebook.litho.annotations.OnPopulateExtraAccessibilityNode;
import com.facebook.litho.annotations.OnUnmount;
import com.facebook.litho.annotations.Prop;
import com.facebook.litho.annotations.PropDefault;
import com.facebook.litho.annotations.ResType;
import com.facebook.yoga.YogaDirection;
import com.facebook.fbui.textlayoutbuilder.TextLayoutBuilder;
import com.facebook.fbui.textlayoutbuilder.util.LayoutMeasureUtil;
import com.facebook.widget.accessibility.delegates.AccessibleClickableSpan;

import static android.support.v4.widget.ExploreByTouchHelper.INVALID_ID;
import static android.text.Layout.Alignment.ALIGN_NORMAL;
import static com.facebook.litho.SizeSpec.AT_MOST;
import static com.facebook.litho.SizeSpec.EXACTLY;
import static com.facebook.litho.SizeSpec.UNSPECIFIED;
import static com.facebook.litho.annotations.ResType.BOOL;
import static com.facebook.litho.annotations.ResType.STRING;

@MountSpec(isPureRender = true, shouldUseDisplayList = true, poolSize = 30)
class TextSpec {

  private static final Alignment[] ALIGNMENT = Alignment.values();
  private static final TruncateAt[] TRUNCATE_AT = TruncateAt.values();

  private static final Typeface DEFAULT_TYPEFACE = Typeface.DEFAULT;
  private static final int DEFAULT_COLOR = 0;
  private static final int DEFAULT_EMS = -1;
  private static final int DEFAULT_MIN_WIDTH = 0;
  private static final int DEFAULT_MAX_WIDTH = Integer.MAX_VALUE;

  private static final int[][] DEFAULT_TEXT_COLOR_STATE_LIST_STATES = {{0}};
  private static final int[] DEFAULT_TEXT_COLOR_STATE_LIST_COLORS = {Color.BLACK};

  private static final String TAG = "TextSpec";

  @PropDefault protected static final int minLines = Integer.MIN_VALUE;
  @PropDefault protected static final int maxLines = Integer.MAX_VALUE;
  @PropDefault protected static final int minEms = DEFAULT_EMS;
  @PropDefault protected static final int maxEms = DEFAULT_EMS;
  @PropDefault protected static final int minWidth = DEFAULT_MIN_WIDTH;
  @PropDefault protected static final int maxWidth = DEFAULT_MAX_WIDTH;
  @PropDefault protected static final int shadowColor = Color.GRAY;
  @PropDefault protected static final int textColor = DEFAULT_COLOR;
  @PropDefault protected static final int linkColor = DEFAULT_COLOR;
  @PropDefault protected static final ColorStateList textColorStateList = new ColorStateList(
      DEFAULT_TEXT_COLOR_STATE_LIST_STATES,
      DEFAULT_TEXT_COLOR_STATE_LIST_COLORS);
  @PropDefault protected static final int textSize = 13;
  @PropDefault protected static final int textStyle = DEFAULT_TYPEFACE.getStyle();
  @PropDefault protected static final Typeface typeface = DEFAULT_TYPEFACE;
  @PropDefault protected static final float spacingMultiplier = 1.0f;
  @PropDefault protected static final VerticalGravity verticalGravity = VerticalGravity.TOP;
  @PropDefault protected static final boolean glyphWarming = false;
  @PropDefault protected static final boolean shouldIncludeFontPadding = true;
  @PropDefault protected static final Alignment textAlignment = ALIGN_NORMAL;

  private static final Path sTempPath = new Path();
  private static final Rect sTempRect = new Rect();
  private static final RectF sTempRectF = new RectF();

  private static final SynchronizedPool<TextLayoutBuilder> sTextLayoutBuilderPool =
      new SynchronizedPool<>(2);

  @OnLoadStyle
  static void onLoadStyle(
      ComponentContext c,
      Output<TruncateAt> ellipsize,
      Output<Boolean> shouldIncludeFontPadding,
      Output<Float> spacingMultiplier,
      Output<Integer> minLines,
      Output<Integer> maxLines,
      Output<Integer> minEms,
      Output<Integer> maxEms,
      Output<Integer> minWidth,
      Output<Integer> maxWidth,
      Output<Boolean> isSingleLine,
      Output<CharSequence> text,
      Output<ColorStateList> textColorStateList,
      Output<Integer> linkColor,
      Output<Integer> highlightColor,
      Output<Integer> textSize,
      Output<Alignment> textAlignment,
      Output<Integer> textStyle,
      Output<Float> shadowRadius,
      Output<Float> shadowDx,
      Output<Float> shadowDy,
      Output<Integer> shadowColor) {

    final TypedArray a = c.obtainStyledAttributes(R.styleable.Text, 0);

    for (int i = 0, size = a.getIndexCount(); i < size; i++) {
      final int attr = a.getIndex(i);

      if (attr == R.styleable.Text_android_text) {
        text.set(a.getString(attr));
      } else if (attr == R.styleable.Text_android_textColor) {
        textColorStateList.set(a.getColorStateList(attr));
      } else if (attr == R.styleable.Text_android_textSize) {
        textSize.set(a.getDimensionPixelSize(attr, 0));
      } else if (attr == R.styleable.Text_android_ellipsize) {
        final int index = a.getInteger(attr, 0);
        if (index > 0) {
          ellipsize.set(TRUNCATE_AT[index - 1]);
        }
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 &&
          attr == R.styleable.Text_android_textAlignment) {
        textAlignment.set(ALIGNMENT[a.getInteger(attr, 0)]);
      } else if (attr == R.styleable.Text_android_includeFontPadding) {
        shouldIncludeFontPadding.set(a.getBoolean(attr, false));
      } else if (attr == R.styleable.Text_android_minLines) {
        minLines.set(a.getInteger(attr, -1));
      } else if (attr == R.styleable.Text_android_maxLines) {
        maxLines.set(a.getInteger(attr, -1));
      } else if (attr == R.styleable.Text_android_singleLine) {
        isSingleLine.set(a.getBoolean(attr, false));
      } else if (attr == R.styleable.Text_android_textColorLink) {
        linkColor.set(a.getColor(attr, 0));
      } else if (attr == R.styleable.Text_android_textColorHighlight) {
        highlightColor.set(a.getColor(attr, 0));
      } else if (attr == R.styleable.Text_android_textStyle) {
        textStyle.set(a.getInteger(attr, 0));
      } else if (attr == R.styleable.Text_android_lineSpacingMultiplier) {
        spacingMultiplier.set(a.getFloat(attr, 0));
      } else if (attr == R.styleable.Text_android_shadowDx) {
        shadowDx.set(a.getFloat(attr, 0));
      } else if (attr == R.styleable.Text_android_shadowDy) {
        shadowDy.set(a.getFloat(attr, 0));
      } else if (attr == R.styleable.Text_android_shadowRadius) {
        shadowRadius.set(a.getFloat(attr, 0));
      } else if (attr == R.styleable.Text_android_shadowColor) {
        shadowColor.set(a.getColor(attr, 0));
      } else if (attr == R.styleable.Text_android_minEms) {
        minEms.set(a.getInteger(attr, DEFAULT_EMS));
      } else if (attr == R.styleable.Text_android_maxEms) {
        maxEms.set(a.getInteger(attr, DEFAULT_EMS));
      } else if (attr == R.styleable.Text_android_minWidth) {
        minWidth.set(a.getInteger(attr, DEFAULT_MIN_WIDTH));
      } else if (attr == R.styleable.Text_android_maxWidth) {
        maxWidth.set(a.getInteger(attr, DEFAULT_MAX_WIDTH));
      }
    }

    a.recycle();
  }

  @OnMeasure
  static void onMeasure(
      ComponentContext context,
      ComponentLayout layout,
      int widthSpec,
      int heightSpec,
      Size size,
      @Prop(resType = ResType.STRING) CharSequence text,
      @Prop(optional = true) TruncateAt ellipsize,
      @Prop(optional = true, resType = ResType.BOOL) boolean shouldIncludeFontPadding,
      @Prop(optional = true, resType = ResType.INT) int minLines,
      @Prop(optional = true, resType = ResType.INT) int maxLines,
      @Prop(optional = true, resType = ResType.INT) int minEms,
      @Prop(optional = true, resType = ResType.INT) int maxEms,
      @Prop(optional = true, resType = ResType.INT) int minWidth,
      @Prop(optional = true, resType = ResType.INT) int maxWidth,
      @Prop(optional = true, resType = ResType.DIMEN_OFFSET) float shadowRadius,
      @Prop(optional = true, resType = ResType.DIMEN_OFFSET) float shadowDx,
      @Prop(optional = true, resType = ResType.DIMEN_OFFSET) float shadowDy,
      @Prop(optional = true, resType = ResType.COLOR) int shadowColor,
      @Prop(optional = true, resType = ResType.BOOL) boolean isSingleLine,
      @Prop(optional = true, resType = ResType.COLOR) int textColor,
      @Prop(optional = true) ColorStateList textColorStateList,
      @Prop(optional = true, resType = ResType.COLOR) int linkColor,
      @Prop(optional = true, resType = ResType.DIMEN_TEXT) int textSize,
      @Prop(optional = true, resType = ResType.DIMEN_OFFSET) float extraSpacing,
      @Prop(optional = true, resType = ResType.FLOAT) float spacingMultiplier,
      @Prop(optional = true) int textStyle,
      @Prop(optional = true) Typeface typeface,
      @Prop(optional = true) Alignment textAlignment,
      @Prop(optional = true) boolean glyphWarming,
      @Prop(optional = true) TextDirectionHeuristicCompat textDirection,
      Output<Layout> measureLayout,
      Output<Integer> measuredWidth,
      Output<Integer> measuredHeight) {

    if (TextUtils.isEmpty(text)) {
      measureLayout.set(null);
      size.width = 0;
      size.height = 0;
      return;
    }

    Layout newLayout = createTextLayout(
        widthSpec,
        ellipsize,
        shouldIncludeFontPadding,
        maxLines,
        shadowRadius,
        shadowDx,
        shadowDy,
        shadowColor,
        isSingleLine,
        text,
        textColor,
        textColorStateList,
        linkColor,
        textSize,
        extraSpacing,
        spacingMultiplier,
        textStyle,
        typeface,
        textAlignment,
        glyphWarming,
        layout.getResolvedLayoutDirection(),
        minEms,
        maxEms,
        minWidth,
        maxWidth,
        textDirection);

    measureLayout.set(newLayout);

    size.width = SizeSpec.resolveSize(widthSpec, newLayout.getWidth());

    // Adjust height according to the minimum number of lines.
    int preferredHeight = LayoutMeasureUtil.getHeight(newLayout);
    final int lineCount = newLayout.getLineCount();
    if (lineCount < minLines) {
      final TextPaint paint = newLayout.getPaint();

      final int lineHeight =
          Math.round(paint.getFontMetricsInt(null) * spacingMultiplier + extraSpacing);
      preferredHeight += lineHeight * (minLines - lineCount);
    }

    size.height = SizeSpec.resolveSize(heightSpec, preferredHeight);

    // Some devices seem to be returning negative sizes in some cases.
    if (size.width < 0 || size.height < 0) {
      size.width = Math.max(size.width, 0);
      size.height = Math.max(size.height, 0);

      final ComponentsLogger logger = context.getLogger();
      if (logger != null) {
        logger.softError("Text layout measured to less than 0 pixels");
      }
    }

    measuredWidth.set(size.width);
    measuredHeight.set(size.height);
  }

  private static Layout createTextLayout(
      int widthSpec,
      TruncateAt ellipsize,
      boolean shouldIncludeFontPadding,
      int maxLines,
      float shadowRadius,
      float shadowDx,
      float shadowDy,
      int shadowColor,
      boolean isSingleLine,
      CharSequence text,
      int textColor,
      ColorStateList textColorStateList,
      int linkColor,
      int textSize,
      float extraSpacing,
      float spacingMultiplier,
      int textStyle,
      Typeface typeface,
      Alignment textAlignment,
      boolean glyphWarming,
      YogaDirection layoutDirection,
      int minEms,
      int maxEms,
      int minWidth,
      int maxWidth,
      TextDirectionHeuristicCompat textDirection) {
    Layout newLayout;

    TextLayoutBuilder layoutBuilder = sTextLayoutBuilderPool.acquire();
    if (layoutBuilder == null) {
      layoutBuilder = new TextLayoutBuilder();
      layoutBuilder.setShouldCacheLayout(false);
    }

    final @TextLayoutBuilder.MeasureMode int textMeasureMode;
    switch (SizeSpec.getMode(widthSpec)) {
      case UNSPECIFIED:
        textMeasureMode = TextLayoutBuilder.MEASURE_MODE_UNSPECIFIED;
        break;
      case EXACTLY:
        textMeasureMode = TextLayoutBuilder.MEASURE_MODE_EXACTLY;
        break;
      case AT_MOST:
        textMeasureMode = TextLayoutBuilder.MEASURE_MODE_AT_MOST;
        break;
      default:
        throw new IllegalStateException("Unexpected size mode: " + SizeSpec.getMode(widthSpec));
    }

    layoutBuilder
        .setEllipsize(ellipsize)
        .setMaxLines(maxLines)
        .setShadowLayer(shadowRadius, shadowDx, shadowDy, shadowColor)
        .setSingleLine(isSingleLine)
        .setText(text)
        .setTextSize(textSize)
        .setWidth(
            SizeSpec.getSize(widthSpec),
            textMeasureMode);

    if (minEms != DEFAULT_EMS) {
      layoutBuilder.setMinEms(minEms);
    } else {
      layoutBuilder.setMinWidth(minWidth);
    }

    if (maxEms != DEFAULT_EMS) {
      layoutBuilder.setMaxEms(maxEms);
    } else {
      layoutBuilder.setMaxWidth(maxWidth);
    }

    if (textColor != 0) {
      layoutBuilder.setTextColor(textColor);
    } else {
      layoutBuilder.setTextColor(textColorStateList);
    }

    if (typeface != DEFAULT_TYPEFACE) {
      layoutBuilder.setTypeface(typeface);
    } else {
      layoutBuilder.setTextStyle(textStyle);
    }

    if (textDirection != null) {
      layoutBuilder.setTextDirection(textDirection);
    } else {
      layoutBuilder.setTextDirection(layoutDirection == YogaDirection.RTL
          ? TextDirectionHeuristicsCompat.FIRSTSTRONG_RTL
          : TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR);
    }

    layoutBuilder.setIncludeFontPadding(shouldIncludeFontPadding);
    layoutBuilder.setTextSpacingExtra(extraSpacing);
    layoutBuilder.setTextSpacingMultiplier(spacingMultiplier);
    layoutBuilder.setAlignment(textAlignment);
    layoutBuilder.setLinkColor(linkColor);

    newLayout = layoutBuilder.build();

    layoutBuilder.setText(null);
    sTextLayoutBuilderPool.release(layoutBuilder);

    if (glyphWarming && !ComponentsConfiguration.shouldGenerateDisplayLists) {
      GlyphWarmer.getInstance().warmLayout(newLayout);
    }

    return newLayout;
  }

  @OnBoundsDefined
  static void onBoundsDefined(
      ComponentContext c,
      ComponentLayout layout,
      @Prop(resType = ResType.STRING) CharSequence text,
      @Prop(optional = true) TruncateAt ellipsize,
      @Prop(optional = true, resType = ResType.BOOL) boolean shouldIncludeFontPadding,
      @Prop(optional = true, resType = ResType.INT) int maxLines,
      @Prop(optional = true, resType = ResType.INT) int minEms,
      @Prop(optional = true, resType = ResType.INT) int maxEms,
      @Prop(optional = true, resType = ResType.INT) int minWidth,
      @Prop(optional = true, resType = ResType.INT) int maxWidth,
      @Prop(optional = true, resType = ResType.DIMEN_OFFSET) float shadowRadius,
      @Prop(optional = true, resType = ResType.DIMEN_OFFSET) float shadowDx,
      @Prop(optional = true, resType = ResType.DIMEN_OFFSET) float shadowDy,
      @Prop(optional = true, resType = ResType.COLOR) int shadowColor,
      @Prop(optional = true, resType = ResType.BOOL) boolean isSingleLine,
      @Prop(optional = true, resType = ResType.COLOR) int textColor,
      @Prop(optional = true) ColorStateList textColorStateList,
      @Prop(optional = true, resType = ResType.COLOR) int linkColor,
      @Prop(optional = true, resType = ResType.DIMEN_TEXT) int textSize,
      @Prop(optional = true, resType = ResType.DIMEN_OFFSET) float extraSpacing,
      @Prop(optional = true, resType = ResType.FLOAT) float spacingMultiplier,
      @Prop(optional = true) VerticalGravity verticalGravity,
      @Prop(optional = true) int textStyle,
      @Prop(optional = true) Typeface typeface,
      @Prop(optional = true) Alignment textAlignment,
      @Prop(optional = true) boolean glyphWarming,
      @Prop(optional = true) TextDirectionHeuristicCompat textDirection,
      @FromMeasure Layout measureLayout,
      @FromMeasure Integer measuredWidth,
      @FromMeasure Integer measuredHeight,
      Output<Layout> textLayout,
      Output<Float> textLayoutTranslationY,
      Output<ClickableSpan[]> clickableSpans) {

    if (TextUtils.isEmpty(text)) {
      return;
    }

    final float layoutWidth =
        layout.getWidth() - layout.getPaddingLeft() - layout.getPaddingRight();
    final float layoutHeight =
        layout.getHeight() - layout.getPaddingTop() - layout.getPaddingBottom();

    if (measureLayout != null &&
        measuredWidth == layoutWidth &&
        measuredHeight == layoutHeight) {
      textLayout.set(measureLayout);
    } else {
      if (measureLayout != null) {
        Log.w(
            TAG,
            "Remeasuring Text component.  This is expensive: consider changing parent layout " +
                "so that double measurement is not necessary.");
          }

      textLayout.set(
          createTextLayout(
              SizeSpec.makeSizeSpec((int) layoutWidth, EXACTLY),
              ellipsize,
              shouldIncludeFontPadding,
              maxLines,
              shadowRadius,
              shadowDx,
              shadowDy,
              shadowColor,
              isSingleLine,
              text,
              textColor,
              textColorStateList,
              linkColor,
              textSize,
              extraSpacing,
              spacingMultiplier,
              textStyle,
              typeface,
              textAlignment,
              glyphWarming,
              layout.getResolvedLayoutDirection(),
              minEms,
              maxEms,
              minWidth,
              maxWidth,
              textDirection));
    }

    final float textHeight = LayoutMeasureUtil.getHeight(textLayout.get());

    switch (verticalGravity) {
      case CENTER:
        textLayoutTranslationY.set((layoutHeight - textHeight) / 2);
        break;

      case BOTTOM:
        textLayoutTranslationY.set(layoutHeight - textHeight);
        break;

      default:
        textLayoutTranslationY.set(0f);
        break;
    }

    if (text instanceof Spanned) {
      clickableSpans.set(((Spanned) text).getSpans(
          0,
          text.length() - 1,
          ClickableSpan.class));
    }
  }

  @OnCreateMountContent
  static TextDrawable onCreateMountContent(ComponentContext c) {
    return new TextDrawable();
  }

  @OnMount
  static void onMount(
      ComponentContext c,
      TextDrawable textDrawable,
      @Prop(resType = ResType.STRING) CharSequence text,
      @Prop(optional = true, resType = ResType.COLOR) int textColor,
      @Prop(optional = true, resType = ResType.COLOR) int highlightColor,
      @Prop(optional = true) ColorStateList textColorStateList,
      @FromBoundsDefined Layout textLayout,
