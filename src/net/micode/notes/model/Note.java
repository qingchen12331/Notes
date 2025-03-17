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

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;

import java.util.ArrayList;

public class Note {
    // 存储笔记的差异值（即修改后的数据）
    private ContentValues mNoteDiffValues;
    // 内部类，用于处理笔记的具体内容（如文本或通话数据）
    private NoteData mNoteData;
    // 日志标签
    private static final String TAG = "Note";

    /**
     * 创建一个新的笔记ID并添加到数据库中。
     * 此方法是线程安全的，因为使用了synchronized关键字。
     * @param context 应用上下文
     * @param folderId 文件夹ID
     * @return 新创建的笔记ID
     */
    public static synchronized long getNewNoteId(Context context, long folderId) {
        ContentValues values = new ContentValues();
        long createdTime = System.currentTimeMillis();

        // 设置笔记的创建时间和修改时间
        values.put(NoteColumns.CREATED_DATE, createdTime);
        values.put(NoteColumns.MODIFIED_DATE, createdTime);

        // 设置笔记类型为普通笔记
        values.put(NoteColumns.TYPE, Notes.TYPE_NOTE);

        // 标记为本地修改
        values.put(NoteColumns.LOCAL_MODIFIED, 1);

        // 设置父文件夹ID
        values.put(NoteColumns.PARENT_ID, folderId);

        // 插入新笔记到数据库中，并获取返回的URI
        Uri uri = context.getContentResolver().insert(Notes.CONTENT_NOTE_URI, values);

        long noteId = 0;
        try {
            // 从返回的URI中提取笔记ID
            noteId = Long.valueOf(uri.getPathSegments().get(1));
        } catch (NumberFormatException e) {
            // 如果无法解析笔记ID，则记录错误日志
            Log.e(TAG, "Get note id error :" + e.toString());
            noteId = 0;
        }

        // 检查是否获取到了有效的笔记ID
        if (noteId == -1) {
            throw new IllegalStateException("Wrong note id:" + noteId);
        }

        return noteId;
    }

    // 构造函数，初始化mNoteDiffValues和mNoteData
    public Note() {
        mNoteDiffValues = new ContentValues();
        mNoteData = new NoteData();
    }

    /**
     * 设置笔记的键值对，并标记为已修改。
     * @param key 键
     * @param value 值
     */
    public void setNoteValue(String key, String value) {
        mNoteDiffValues.put(key, value);
        // 标记为本地修改
        mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
        // 更新修改时间
        mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
    }

    /**
     * 设置文本数据。
     * @param key 键
     * @param value 值
     */
    public void setTextData(String key, String value) {
        mNoteData.setTextData(key, value);
    }

    /**
     * 设置文本数据ID。
     * @param id 文本数据ID
     */
    public void setTextDataId(long id) {
        mNoteData.setTextDataId(id);
    }

    /**
     * 获取文本数据ID。
     * @return 文本数据ID
     */
    public long getTextDataId() {
        return mNoteData.mTextDataId;
    }

    /**
     * 设置通话数据ID。
     * @param id 通话数据ID
     */
    public void setCallDataId(long id) {
        mNoteData.setCallDataId(id);
    }

    /**
     * 设置通话数据。
     * @param key 键
     * @param value 值
     */
    public void setCallData(String key, String value) {
        mNoteData.setCallData(key, value);
    }

    /**
     * 检查是否有本地修改。
     * @return 是否有本地修改
     */
    public boolean isLocalModified() {
        return mNoteDiffValues.size() > 0 || mNoteData.isLocalModified();
    }

    /**
     * 将本地修改同步到数据库。
     * @param context 应用上下文
     * @param noteId 笔记ID
     * @return 是否成功同步
     */
    public boolean syncNote(Context context, long noteId) {
        // 检查笔记ID是否有效
        if (noteId <= 0) {
            throw new IllegalArgumentException("Wrong note id:" + noteId);
        }

        // 如果没有本地修改，则直接返回成功
        if (!isLocalModified()) {
            return true;
        }

        // 理论上，一旦数据发生变化，应更新LOCAL_MODIFIED和MODIFIED_DATE字段
        // 为了数据安全，即使更新失败也继续执行后续操作
        if (context.getContentResolver().update(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), mNoteDiffValues, null,
                null) == 0) {
            Log.e(TAG, "Update note error, should not happen");
            // 不返回，继续执行
        }
        mNoteDiffValues.clear();

        // 如果NoteData中有本地修改，则尝试将其推送到内容提供者
        if (mNoteData.isLocalModified()
                && (mNoteData.pushIntoContentResolver(context, noteId) == null)) {
            return false;
        }

        return true;
    }

    /**
     * 内部类，用于处理笔记的具体内容（如文本或通话数据）。
     */
    private class NoteData {
        // 文本数据ID
        private long mTextDataId;
        // 文本数据值
        private ContentValues mTextDataValues;
        // 通话数据ID
        private long mCallDataId;
        // 通话数据值
        private ContentValues mCallDataValues;
        // 日志标签
        private static final String TAG = "NoteData";

        // 构造函数，初始化成员变量
        public NoteData() {
            mTextDataValues = new ContentValues();
            mCallDataValues = new ContentValues();
            mTextDataId = 0;
            mCallDataId = 0;
        }

        /**
         * 检查是否有本地修改。
         * @return 是否有本地修改
         */
        boolean isLocalModified() {
            return mTextDataValues.size() > 0 || mCallDataValues.size() > 0;
        }

        /**
         * 设置文本数据ID。
         * @param id 文本数据ID
         */
        void setTextDataId(long id) {
            if(id <= 0) {
                throw new IllegalArgumentException("Text data id should larger than 0");
            }
            mTextDataId = id;
        }

        /**
         * 设置通话数据ID。
         * @param id 通话数据ID
         */
        void setCallDataId(long id) {
            if (id <= 0) {
                throw new IllegalArgumentException("Call data id should larger than 0");
            }
            mCallDataId = id;
        }

        /**
         * 设置通话数据，并标记为已修改。
         * @param key 键
         * @param value 值
         */
        void setCallData(String key, String value) {
            mCallDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1); // 标记为本地修改
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis()); // 更新修改时间
        }

        /**
         * 设置文本数据，并标记为已修改。
         * @param key 键
         * @param value 值
         */
        void setTextData(String key, String value) {
            mTextDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1); // 标记为本地修改
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis()); // 更新修改时间
        }

        /**
         * 将数据推送到内容提供者。
         * @param context 应用上下文
         * @param noteId 笔记ID
         * @return 返回更新后的URI或null
         */
        Uri pushIntoContentResolver(Context context, long noteId) {
            // 检查笔记ID是否有效
            if (noteId <= 0) {
                throw new IllegalArgumentException("Wrong note id:" + noteId);
            }

            ArrayList<ContentProviderOperation> operationList = new ArrayList<>();
            ContentProviderOperation.Builder builder = null;

            // 处理文本数据
            if(mTextDataValues.size() > 0) {
                mTextDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mTextDataId == 0) {
                    // 如果文本数据ID为0，则插入新数据
                    mTextDataValues.put(DataColumns.MIME_TYPE, TextNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI, mTextDataValues);
                    try {
                        setTextDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new text data fail with noteId" + noteId); // 错误日志
                        mTextDataValues.clear();
                        return null;
                    }
                } else {
                    // 否则更新现有数据
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mTextDataId));
                    builder.withValues(mTextDataValues);
                    operationList.add(builder.build());
                }
                mTextDataValues.clear(); // 清空文本数据值
            }

            // 处理通话数据
            if(mCallDataValues.size() > 0) {
                mCallDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mCallDataId == 0) {
                    // 如果通话数据ID为0，则插入新数据
                    mCallDataValues.put(DataColumns.MIME_TYPE, CallNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI, mCallDataValues);
                    try {
                        setCallDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new call data fail with noteId" + noteId); // 错误日志
                        mCallDataValues.clear();
                        return null;
                    }
                } else {
                    // 否则更新现有数据
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mCallDataId));
                    builder.withValues(mCallDataValues);
                    operationList.add(builder.build());
                }
                mCallDataValues.clear(); // 清空通话数据值
            }

            // 如果有需要执行的操作
            if (operationList.size() > 0) {
                try {
                    // 执行批量操作
                    ContentProviderResult[] results = context.getContentResolver().applyBatch(
                            Notes.AUTHORITY, operationList);
                    return (results == null || results.length == 0 || results[0] == null) ? null
                            : ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                } catch (RemoteException | OperationApplicationException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage())); // 错误日志
                    return null;
                }
            }
            return null;
        }
    }
}