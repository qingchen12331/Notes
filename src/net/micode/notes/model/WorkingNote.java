/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.model;

import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.tool.ResourceParser.NoteBgResources;

/**
 * WorkingNote 类用于封装笔记的创建、加载、保存以及属性修改等功能。
 * 它提供了一个操作笔记的接口，同时支持监听笔记设置的变更。
 */
public class WorkingNote {
    // 当前正在操作的笔记对象
    private Note mNote;
    // 笔记的唯一标识符
    private long mNoteId;
    // 笔记的内容
    private String mContent;
    // 笔记的模式（例如普通笔记模式或清单模式）
    private int mMode;

    // 笔记的提醒时间
    private long mAlertDate;

    // 笔记的最后修改时间
    private long mModifiedDate;

    // 笔记的背景颜色 ID
    private int mBgColorId;

    // 笔记关联的小部件 ID
    private int mWidgetId;

    // 笔记的小部件类型
    private int mWidgetType;

    // 笔记所属的文件夹 ID
    private long mFolderId;

    // 上下文对象，用于访问应用资源和数据库
    private Context mContext;

    // 日志标签，用于调试
    private static final String TAG = "WorkingNote";

    // 标记笔记是否已被删除
    private boolean mIsDeleted;

    // 笔记设置变更监听器
    private NoteSettingChangedListener mNoteSettingStatusListener;

    /**
     * 数据库查询时使用的字段投影，用于获取笔记数据。
     */
    public static final String[] DATA_PROJECTION = new String[] {
            DataColumns.ID,              // 数据 ID
            DataColumns.CONTENT,         // 数据内容
            DataColumns.MIME_TYPE,       // 数据类型（例如普通笔记或电话笔记）
            DataColumns.DATA1,           // 数据字段 1
            DataColumns.DATA2,           // 数据字段 2
            DataColumns.DATA3,           // 数据字段 3
            DataColumns.DATA4            // 数据字段 4
    };

    /**
     * 数据库查询时使用的字段投影，用于获取笔记元数据。
     */
    public static final String[] NOTE_PROJECTION = new String[] {
            NoteColumns.PARENT_ID,       // 父文件夹 ID
            NoteColumns.ALERTED_DATE,    // 提醒时间
            NoteColumns.BG_COLOR_ID,     // 背景颜色 ID
            NoteColumns.WIDGET_ID,       // 小部件 ID
            NoteColumns.WIDGET_TYPE,     // 小部件类型
            NoteColumns.MODIFIED_DATE    // 最后修改时间
    };

    /**
     * 数据库查询结果中各字段对应的列索引。
     */
    private static final int DATA_ID_COLUMN = 0;
    private static final int DATA_CONTENT_COLUMN = 1;
    private static final int DATA_MIME_TYPE_COLUMN = 2;
    private static final int DATA_MODE_COLUMN = 3;

    private static final int NOTE_PARENT_ID_COLUMN = 0;
    private static final int NOTE_ALERTED_DATE_COLUMN = 1;
    private static final int NOTE_BG_COLOR_ID_COLUMN = 2;
    private static final int NOTE_WIDGET_ID_COLUMN = 3;
    private static final int NOTE_WIDGET_TYPE_COLUMN = 4;
    private static final int NOTE_MODIFIED_DATE_COLUMN = 5;

    /**
     * 构造一个新的笔记实例。
     *
     * @param context 应用上下文
     * @param folderId 笔记所属的文件夹 ID
     */
    private WorkingNote(Context context, long folderId) {
        mContext = context;
        mAlertDate = 0;
        mModifiedDate = System.currentTimeMillis();
        mFolderId = folderId;
        mNote = new Note();
        mNoteId = 0;
        mIsDeleted = false;
        mMode = 0;
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE; // 初始化为无效的小部件类型
    }

    /**
     * 构造一个已存在的笔记实例。
     *
     * @param context 应用上下文
     * @param noteId 笔记的 ID
     * @param folderId 笔记所属的文件夹 ID
     */
    private WorkingNote(Context context, long noteId, long folderId) {
        mContext = context;
        mNoteId = noteId;
        mFolderId = folderId;
        mIsDeleted = false;
        mNote = new Note();
        loadNote(); // 加载笔记数据
    }

    /**
     * 加载笔记的元数据。
     */
    private void loadNote() {
        Cursor cursor = mContext.getContentResolver().query(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mNoteId), NOTE_PROJECTION, null,
                null, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                mFolderId = cursor.getLong(NOTE_PARENT_ID_COLUMN); // 获取父文件夹 ID
                mBgColorId = cursor.getInt(NOTE_BG_COLOR_ID_COLUMN); // 获取背景颜色 ID
                mWidgetId = cursor.getInt(NOTE_WIDGET_ID_COLUMN); // 获取小部件 ID
                mWidgetType = cursor.getInt(NOTE_WIDGET_TYPE_COLUMN); // 获取小部件类型
                mAlertDate = cursor.getLong(NOTE_ALERTED_DATE_COLUMN); // 获取提醒时间
                mModifiedDate = cursor.getLong(NOTE_MODIFIED_DATE_COLUMN); // 获取最后修改时间
            }
            cursor.close();
        } else {
            Log.e(TAG, "No note with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note with id " + mNoteId);
        }
        loadNoteData(); // 加载笔记的具体数据
    }

    /**
     * 加载笔记的具体数据（例如内容、模式等）。
     */
    private void loadNoteData() {
        Cursor cursor = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI, DATA_PROJECTION,
                DataColumns.NOTE_ID + "=?", new String[]{String.valueOf(mNoteId)}, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    String type = cursor.getString(DATA_MIME_TYPE_COLUMN); // 获取数据类型
                    if (DataConstants.NOTE.equals(type)) {
                        mContent = cursor.getString(DATA_CONTENT_COLUMN); // 获取笔记内容
                        mMode = cursor.getInt(DATA_MODE_COLUMN); // 获取笔记模式
                        mNote.setTextDataId(cursor.getLong(DATA_ID_COLUMN)); // 设置文本数据 ID
                    } else if (DataConstants.CALL_NOTE.equals(type)) {
                        mNote.setCallDataId(cursor.getLong(DATA_ID_COLUMN)); // 设置电话笔记数据 ID
                    } else {
                        Log.d(TAG, "Wrong note type with type:" + type);
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
        } else {
            Log.e(TAG, "No data with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note's data with id " + mNoteId);
        }
    }

    /**
     * 创建一个空的笔记实例。
     *
     * @param context 应用上下文
     * @param folderId 笔记所属的文件夹 ID
     * @param widgetId 小部件 ID
     * @param widgetType 小部件类型
     * @param defaultBgColorId 默认背景颜色 ID
     * @return 新创建的笔记实例
     */
    public static WorkingNote createEmptyNote(Context context, long folderId, int widgetId,
                                              int widgetType, int defaultBgColorId) {
        WorkingNote note = new WorkingNote(context, folderId);
        note.setBgColorId(defaultBgColorId); // 设置默认背景颜色
        note.setWidgetId(widgetId); // 设置小部件 ID
        note.setWidgetType(widgetType); // 设置小部件类型
        return note;
    }

    /**
     * 根据笔记 ID 加载一个已存在的笔记实例。
     *
     * @param context 应用上下文
     * @param id 笔记的 ID
     * @return 加载的笔记实例
     */
    public static WorkingNote load(Context context, long id) {
        return new WorkingNote(context,        return new WorkingNote(context, id, 0);
    }

    /**
     * 保存笔记到数据库。
     * 如果笔记是新创建的，则会尝试插入到数据库中。
     * 如果笔记已经存在，则会更新数据库中的记录。
     *
     * @return 是否保存成功
     */
    public synchronized boolean saveNote() {
        if (isWorthSaving()) { // 检查笔记是否值得保存（例如内容不为空或有修改）
            if (!existInDatabase()) { // 如果笔记不存在于数据库中
                if ((mNoteId = Note.getNewNoteId(mContext, mFolderId)) == 0) { // 获取新的笔记 ID
                    Log.e(TAG, "Create new note fail with id:" + mNoteId);
                    return false;
                }
            }

            mNote.syncNote(mContext, mNoteId); // 同步笔记数据到数据库

            /**
             * 如果笔记关联了小部件，并且有监听器，则通知小部件内容已更改。
             */
            if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                    && mWidgetType != Notes.TYPE_WIDGET_INVALIDE
                    && mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onWidgetChanged();
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * 检查笔记是否已经存在于数据库中。
     *
     * @return 是否存在于数据库中
     */
    public boolean existInDatabase() {
        return mNoteId > 0;
    }

    /**
     * 检查笔记是否值得保存。
     * 笔记值得保存的条件包括：
     * - 笔记内容不为空（对于新笔记）
     * - 笔记有本地修改（对于已存在的笔记）
     * - 笔记未被标记为删除
     *
     * @return 是否值得保存
     */
    private boolean isWorthSaving() {
        if (mIsDeleted || (!existInDatabase() && TextUtils.isEmpty(mContent))
                || (existInDatabase() && !mNote.isLocalModified())) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * 设置笔记设置变更监听器。
     * 监听器用于响应笔记背景颜色、提醒时间、清单模式等设置的变更。
     *
     * @param l 监听器对象
     */
    public void setOnSettingStatusChangedListener(NoteSettingChangedListener l) {
        mNoteSettingStatusListener = l;
    }

    /**
     * 设置笔记的提醒时间。
     *
     * @param date 提醒时间
     * @param set 是否设置提醒
     */
    public void setAlertDate(long date, boolean set) {
        if (date != mAlertDate) {
            mAlertDate = date;
            mNote.setNoteValue(NoteColumns.ALERTED_DATE, String.valueOf(mAlertDate)); // 更新数据库
        }
        if (mNoteSettingStatusListener != null) {
            mNoteSettingStatusListener.onClockAlertChanged(date, set); // 通知监听器
        }
    }

    /**
     * 标记笔记为删除状态。
     *
     * @param mark 是否标记为删除
     */
    public void markDeleted(boolean mark) {
        mIsDeleted = mark;
        if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                && mWidgetType != Notes.TYPE_WIDGET_INVALIDE && mNoteSettingStatusListener != null) {
            mNoteSettingStatusListener.onWidgetChanged(); // 通知小部件内容已更改
        }
    }

    /**
     * 设置笔记的背景颜色 ID。
     *
     * @param id 背景颜色 ID
     */
    public void setBgColorId(int id) {
        if (id != mBgColorId) {
            mBgColorId = id;
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onBackgroundColorChanged(); // 通知监听器
            }
            mNote.setNoteValue(NoteColumns.BG_COLOR_ID, String.valueOf(id)); // 更新数据库
        }
    }

    /**
     * 设置笔记的清单模式。
     *
     * @param mode 清单模式
     */
    public void setCheckListMode(int mode) {
        if (mMode != mode) {
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onCheckListModeChanged(mMode, mode); // 通知监听器
            }
            mMode = mode;
            mNote.setTextData(TextNote.MODE, String.valueOf(mMode)); // 更新数据库
        }
    }

    /**
     * 设置笔记的小部件类型。
     *
     * @param type 小部件类型
     */
    public void setWidgetType(int type) {
        if (type != mWidgetType) {
            mWidgetType = type;
            mNote.setNoteValue(NoteColumns.WIDGET_TYPE, String.valueOf(mWidgetType)); // 更新数据库
        }
    }

    /**
     * 设置笔记的小部件 ID。
     *
     * @param id 小部件 ID
     */
    public void setWidgetId(int id) {
        if (id != mWidgetId) {
            mWidgetId = id;
            mNote.setNoteValue(NoteColumns.WIDGET_ID, String.valueOf(mWidgetId)); // 更新数据库
        }
    }

    /**
     * 设置笔记的内容。
     *
     * @param text 笔记内容
     */
    public void setWorkingText(String text) {
        if (!TextUtils.equals(mContent, text)) {
            mContent = text;
            mNote.setTextData(DataColumns.CONTENT, mContent); // 更新数据库
        }
    }

    /**
     * 将笔记转换为电话笔记。
     *
     * @param phoneNumber 电话号码
     * @param callDate 通话时间
     */
    public void convertToCallNote(String phoneNumber, long callDate) {
        mNote.setCallData(CallNote.CALL_DATE, String.valueOf(callDate)); // 设置通话时间
        mNote.setCallData(CallNote.PHONE_NUMBER, phoneNumber); // 设置电话号码
        mNote.setNoteValue(NoteColumns.PARENT_ID, String.valueOf(Notes.ID_CALL_RECORD_FOLDER)); // 设置父文件夹为通话记录文件夹
    }

    /**
     * 检查笔记是否设置了提醒时间。
     *
     * @return 是否设置了提醒时间
     */
    public boolean hasClockAlert() {
        return (mAlertDate > 0 ? true : false);
    }

    /**
     * 获取笔记的内容。
     *
     * @return 笔记内容
     */
    public String getContent() {
        return mContent;
    }

    /**
     * 获取笔记的提醒时间。
     *
     * @return 提醒时间
     */
    public long getAlertDate() {
        return mAlertDate;
    }

    /**
     * 获取笔记的最后修改时间。
     *
     * @return 最后修改时间
     */
    public long getModifiedDate() {
        return mModifiedDate;
    }

    /**
     * 获取笔记背景颜色的资源 ID。
     *
     * @return 背景颜色资源 ID
     */
    public int getBgColorResId() {
        return NoteBgResources.getNoteBgResource(mBgColorId);
    }

    /**
     * 获取笔记背景颜色 ID。
     *
     * @return 背景颜色 ID
     */
    public int getBgColorId() {
        return mBgColorId;
    }

    /**
     * 获取笔记标题背景颜色的资源 ID。
     *
     * @return 标题背景颜色资源 ID
     */
    public int getTitleBgResId() {
        return NoteBgResources.getNoteTitleBgResource(mBgColorId);
    }

    /**
     * 获取笔记的清单模式。
     *
     * @return 清单模式
     */
    public int getCheckListMode() {
        return mMode;
    }

    /**
     * 获取笔记的 ID。
     *
     * @return 笔记 ID
     */
    public long getNoteId() {
        return mNoteId;
    }

    /**
     * 获取笔记所属的文件夹 ID。
     *
     * @return 文件夹 ID
     */
    public long getFolderId() {
        return mFolderId;
    }

    /**
     * 获取笔记的小部件 ID。
     *
     * @return 小部件 ID
     */
    public int getWidgetId() {
        return mWidgetId;
    }

    /**
     * 获取笔记的小部件类型。
     *
     * @return 小部件类型
     */
    public int getWidgetType() {
        return mWidgetType;
    }

    /**
     * 笔记设置变更监听器接口。
     * 用于监听笔记背景颜色、提醒时间、清单模式等设置的变更。
     */
    public interface NoteSettingChangedListener {
        /**
         * 当笔记的背景颜色发生变更时调用。
         */
        void onBackgroundColorChanged();

        /**
         * 当用户设置或取消提醒时间时调用。
         *
         * @param date 提醒时间
         * @param set 是否设置了提醒
         */
        void onClockAlertChanged(long date, boolean set);

        /**
         * 当笔记的小部件内容发生变更时调用。
         */
        void onWidgetChanged();

        /**
         * 当笔记的清单模式发生变更时调用。
         *
         * @param oldMode 变更前的模式
         * @param newMode 变更后的模式
         */
        void onCheckListModeChanged(int oldMode, int newMode);
    }
}