package org.wordpress.android.ui.notifications.blocks;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.android.volley.toolbox.NetworkImageView;

import org.json.JSONException;
import org.json.JSONObject;
import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.models.CommentStatus;
import org.wordpress.android.ui.comments.CommentUtils;
import org.wordpress.android.ui.notifications.utils.NotificationsUtils;
import org.wordpress.android.util.DateTimeUtils;
import org.wordpress.android.util.PhotonUtils;

// A user block with slightly different formatting for display in a comment detail
public class CommentUserNoteBlock extends UserNoteBlock {

    private CommentStatus mCommentStatus = CommentStatus.UNKNOWN;
    private int mTextViewIndent;
    private int mNormalBackgroundColor;
    private int mNormalTextColor;
    private int mAgoTextColor;
    private int mUnapprovedTextColor;
    private int mIndentedLeftPadding;

    private boolean mStatusChanged;

    public interface OnCommentStatusChangeListener {
        public void onCommentStatusChanged(CommentStatus newStatus);
    }

    public CommentUserNoteBlock(Context context, JSONObject noteObject,
                                OnNoteBlockTextClickListener onNoteBlockTextClickListener,
                                OnGravatarClickedListener onGravatarClickedListener) {
        super(context, noteObject, onNoteBlockTextClickListener, onGravatarClickedListener);

        if (context != null) {
            setAvatarSize(context.getResources().getDimensionPixelSize(R.dimen.avatar_sz_small));
        }
    }

    @Override
    public BlockType getBlockType() {
        return BlockType.USER_COMMENT;
    }

    @Override
    public int getLayoutResourceId() {
        return R.layout.note_block_comment_user;
    }

    @Override
    public View configureView(View view) {
        final CommentUserNoteBlockHolder noteBlockHolder = (CommentUserNoteBlockHolder)view.getTag();

        noteBlockHolder.nameTextView.setText(getNoteText().toString());
        noteBlockHolder.agoTextView.setText(DateTimeUtils.timestampToTimeSpan(getTimestamp()));

        if (hasImageMediaItem()) {
            String imageUrl = PhotonUtils.fixAvatar(getNoteMediaItem().optString("url", ""), getAvatarSize());
            noteBlockHolder.avatarImageView.setImageUrl(imageUrl, WordPress.imageLoader);
            if (!TextUtils.isEmpty(getUserUrl())) {
                noteBlockHolder.avatarImageView.setOnTouchListener(mOnGravatarTouchListener);
            } else {
                noteBlockHolder.avatarImageView.setOnTouchListener(null);
            }
        } else {
            noteBlockHolder.avatarImageView.setImageResource(R.drawable.placeholder);
            noteBlockHolder.avatarImageView.setOnTouchListener(null);
        }

        CommentUtils.indentTextViewFirstLine(
                noteBlockHolder.commentTextView,
                NotificationsUtils.getSpannableTextFromIndices(getNoteData().optJSONObject("comment_text"), getOnNoteBlockTextClickListener()),
                mTextViewIndent
        );

        // Change display based on comment status and type:
        // 1. Comment replies are indented and have a 'pipe' background
        // 2. Unapproved comments have different background and text color
        int paddingLeft = view.getPaddingLeft();
        int paddingTop = view.getPaddingTop();
        int paddingRight = view.getPaddingRight();
        int paddingBottom = view.getPaddingBottom();
        if (mCommentStatus == CommentStatus.UNAPPROVED) {
            if (hasCommentNestingLevel()) {
                paddingLeft = mIndentedLeftPadding;
                view.setBackgroundResource(R.drawable.comment_reply_unapproved_background);
            } else {
                view.setBackgroundResource(R.drawable.comment_unapproved_background);
            }

            noteBlockHolder.dividerView.setVisibility(View.INVISIBLE);

            noteBlockHolder.agoTextView.setTextColor(mUnapprovedTextColor);
            noteBlockHolder.nameTextView.setTextColor(mUnapprovedTextColor);
            noteBlockHolder.commentTextView.setTextColor(mUnapprovedTextColor);
        } else {
            if (hasCommentNestingLevel()) {
                paddingLeft = mIndentedLeftPadding;
                view.setBackgroundResource(R.drawable.comment_reply_background);
                noteBlockHolder.dividerView.setVisibility(View.INVISIBLE);
            } else {
                view.setBackgroundColor(mNormalBackgroundColor);
                noteBlockHolder.dividerView.setVisibility(View.VISIBLE);
            }

            noteBlockHolder.agoTextView.setTextColor(mAgoTextColor);
            noteBlockHolder.nameTextView.setTextColor(mNormalTextColor);
            noteBlockHolder.commentTextView.setTextColor(mNormalTextColor);
        }

        view.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);

        // If status was changed, fade in the view
        if (mStatusChanged) {
            mStatusChanged = false;
            view.setAlpha(0.4f);
            view.animate().alpha(1.0f).start();
        }

        return view;
    }

    private long getTimestamp() {
        return getNoteData().optInt("timestamp", 0);
    }

    private boolean hasCommentNestingLevel() {
        try {
            JSONObject commentTextObject = getNoteData().getJSONObject("comment_text");
            return commentTextObject.optInt("nest_level", 0) > 0;
        } catch (JSONException e) {
            return false;
        }
    }

    @Override
    public Object getViewHolder(View view) {
        return new CommentUserNoteBlockHolder(view);
    }

    private class CommentUserNoteBlockHolder {
        private final NetworkImageView avatarImageView;
        private final TextView nameTextView;
        private final TextView agoTextView;
        private final TextView commentTextView;
        private final View dividerView;

        public CommentUserNoteBlockHolder(View view) {
            nameTextView = (TextView)view.findViewById(R.id.user_name);
            agoTextView = (TextView)view.findViewById(R.id.user_comment_ago);
            agoTextView.setVisibility(View.VISIBLE);
            commentTextView = (TextView)view.findViewById(R.id.user_comment);
            commentTextView.setMovementMethod(new NoteBlockLinkMovementMethod());
            avatarImageView = (NetworkImageView)view.findViewById(R.id.user_avatar);
            dividerView = view.findViewById(R.id.divider_view);
        }
    }

    public void configureResources(Context context) {
        if (context == null) return;

        mNormalTextColor = context.getResources().getColor(R.color.grey_dark);
        mNormalBackgroundColor = context.getResources().getColor(R.color.white);
        mAgoTextColor = context.getResources().getColor(R.color.grey_medium_dark);
        mUnapprovedTextColor = context.getResources().getColor(R.color.orange_medium);
        mTextViewIndent = context.getResources().getDimensionPixelSize(R.dimen.avatar_sz_small) +
                context.getResources().getDimensionPixelSize(R.dimen.notifications_adjusted_font_margin);
        // Double margin_extra_large for increased indent in comment replies
        mIndentedLeftPadding = context.getResources().getDimensionPixelSize(R.dimen.margin_extra_large) * 2;
    }

    private final OnCommentStatusChangeListener mOnCommentChangedListener = new OnCommentStatusChangeListener() {
        @Override
        public void onCommentStatusChanged(CommentStatus newStatus) {
            mCommentStatus = newStatus;
            mStatusChanged = true;
        }
    };

    public void setCommentStatus(CommentStatus status) {
        mCommentStatus = status;
    }

    public OnCommentStatusChangeListener getOnCommentChangeListener() {
        return mOnCommentChangedListener;
    }
}
