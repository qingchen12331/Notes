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

package net.micode.notes.tool;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * DataUtils 类提供了一系列工具方法，用于操作笔记数据。
 * 包括批量删除笔记、移动笔记、查询文件夹数量、检查笔记可见性等。
 */
public class DataUtils {
    public static final String TAG = "DataUtils";

    /**
     * 批量删除笔记。
     *
     * @param resolver ContentResolver 对象，用于与 ContentProvider 交互。
     * @param ids 要删除的笔记 ID 集合。
     * @return 是否删除成功。
     */
    public static boolean batchDeleteNotes(ContentResolver resolver, HashSet<Long> ids) {
        if (ids == null) {
            Log.d(TAG, "The ids is null");
            return true;
        }
        if (ids.size() == 0) {
            Log.d(TAG, "No id is in the hashset");
            return true;
        }

        ArrayList<ContentProviderOperation> operationList = new ArrayList<>();
        for (long id : ids) {
            if (id == Notes.ID_ROOT_FOLDER) {
                Log.e(TAG, "Don't delete system folder root");
                continue;
            }
            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newDelete(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id));
            operationList.add(builder.build());
        }
        try {
            ContentProviderResult[] results = resolver.applyBatch(Notes.AUTHORITY, operationList);
            if (results == null || results.length == 0 || results[0] == null) {
                Log.d(TAG, "Delete notes failed, ids:" + ids.toString());
                return false;
            }
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
        return false;
    }

    /**
     * 将笔记移动到指定文件夹。
     *
     * @param resolver ContentResolver 对象。
     * @param id 笔记 ID。
     * @param srcFolderId 源文件夹 ID。
     * @param desFolderId 目标文件夹 ID。
     */
    public static void moveNoteToFoler(ContentResolver resolver, long id, long srcFolderId, long desFolderId) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.PARENT_ID, desFolderId);
        values.put(NoteColumns.ORIGIN_PARENT_ID, srcFolderId);
        values.put(NoteColumns.LOCAL_MODIFIED, 1);
        resolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id), values, null, null);
    }

    /**
     * 批量移动笔记到指定文件夹。
     *
     * @param resolver ContentResolver 对象。
     * @param ids 要移动的笔记 ID 集合。
     * @param folderId 目标文件夹 ID。
     * @return 是否移动成功。
     */
    public static boolean batchMoveToFolder(ContentResolver resolver, HashSet<Long> ids,
                                            long folderId) {
        if (ids == null) {
            Log.d(TAG, "The ids is null");
            return true;
        }

        ArrayList<ContentProviderOperation> operationList = new ArrayList<>();
        for (long id : ids) {
            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newUpdate(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id));
            builder.withValue(NoteColumns.PARENT_ID, folderId);
            builder.withValue(NoteColumns.LOCAL_MODIFIED, 1);
            operationList.add(builder.build());
        }

        try {
            ContentProviderResult[] results = resolver.applyBatch(Notes.AUTHORITY, operationList);
            if (results == null || results.length == 0 || results[0] == null) {
                Log.d(TAG, "Move notes failed, ids:" + ids.toString());
                return false;
            }
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
        return false;
    }

    /**
     * 获取用户创建的文件夹数量（不包括系统文件夹）。
     *
     * @param resolver ContentResolver 对象。
     * @return 文件夹数量。
     */
    public static int getUserFolderCount(ContentResolver resolver) {
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI,
                new String[] { "COUNT(*)" },
                NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                new String[] { String.valueOf(Notes.TYPE_FOLDER), String.valueOf(Notes.ID_TRASH_FOLDER)},
                null);

        int count = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                try {
                    count = cursor.getInt(0);
                } catch (IndexOutOfBoundsException e) {
                    Log.e(TAG, "Get folder count failed: " + e.toString());
                } finally {
                    cursor.close();
                }
            }
        }
        return count;
    }

    /**
     * 检查笔记是否在数据库中可见（不包括回收站中的笔记）。
     *
     * @param resolver ContentResolver 对象。
     * @param noteId 笔记 ID。
     * @param type 笔记类型。
     * @return 是否可见。
     */
    public static boolean visibleInNoteDatabase(ContentResolver resolver, long noteId, int type) {
        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                null,
                NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLDER,
                new String[] { String.valueOf(type) },
                null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查笔记是否存在于数据库中。
     *
     * @param resolver ContentResolver 对象。
     * @param noteId 笔记 ID。
     * @return 是否存在。
     */
    public static boolean existInNoteDatabase(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                null, null, null, null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查数据记录是否存在于数据库中。
     *
     * @param resolver ContentResolver 对象。
     * @param dataId 数据记录 ID。
     * @return 是否存在。
     */
    public static boolean existInDataDatabase(ContentResolver resolver, long dataId) {
        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId),
                null, null, null, null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查文件夹名称是否已存在（不包括回收站中的文件夹）。
     *
     * @param resolver ContentResolver 对象。
     * @param name 文件夹名称。
     * @return 是否已存在。
     */
    public static boolean checkVisibleFolderName(ContentResolver resolver, String name) {
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI, null,
                NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER +
                        " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLDER +
                        " AND " + NoteColumns.SNIPPET + "=?",
                new String[] { name }, null);
        boolean        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 获取指定文件夹中所有笔记的小部件属性。
     *
     * @param resolver ContentResolver 对象。
     * @param folderId 文件夹 ID。
     * @return 包含所有笔记小部件属性的集合。
     */
    public static HashSet<AppWidgetAttribute> getFolderNoteWidget(ContentResolver resolver, long folderId) {
        Cursor c = resolver.query(Notes.CONTENT_NOTE_URI,
                new String[] { NoteColumns.WIDGET_ID, NoteColumns.WIDGET_TYPE },
                NoteColumns.PARENT_ID + "=?",
                new String[] { String.valueOf(folderId) },
                null);

        HashSet<AppWidgetAttribute> set = null;
        if (c != null) {
            if (c.moveToFirst()) {
                set = new HashSet<>();
                do {
                    try {
                        AppWidgetAttribute widget = new AppWidgetAttribute();
                        widget.widgetId = c.getInt(0); // 小部件 ID
                        widget.widgetType = c.getInt(1); // 小部件类型
                        set.add(widget);
                    } catch (IndexOutOfBoundsException e) {
                        Log.e(TAG, e.toString());
                    }
                } while (c.moveToNext());
            }
            c.close();
        }
        return set;
    }

    /**
     * 根据笔记 ID 获取电话号码（仅适用于电话笔记）。
     *
     * @param resolver ContentResolver 对象。
     * @param noteId 笔记 ID。
     * @return 电话号码，如果笔记不存在或不是电话笔记，则返回空字符串。
     */
    public static String getCallNumberByNoteId(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(Notes.CONTENT_DATA_URI,
                new String[] { CallNote.PHONE_NUMBER },
                CallNote.NOTE_ID + "=? AND " + CallNote.MIME_TYPE + "=?",
                new String[] { String.valueOf(noteId), CallNote.CONTENT_ITEM_TYPE },
                null);

        if (cursor != null && cursor.moveToFirst()) {
            try {
                return cursor.getString(0); // 获取电话号码
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, "Get call number fails: " + e.toString());
            } finally {
                cursor.close();
            }
        }
        return ""; // 如果笔记不存在或不是电话笔记，返回空字符串
    }

    /**
     * 根据电话号码和通话时间获取对应的笔记 ID（仅适用于电话笔记）。
     *
     * @param resolver ContentResolver 对象。
     * @param phoneNumber 电话号码。
     * @param callDate 通话时间。
     * @return 笔记 ID，如果未找到匹配的笔记，则返回 0。
     */
    public static long getNoteIdByPhoneNumberAndCallDate(ContentResolver resolver, String phoneNumber, long callDate) {
        Cursor cursor = resolver.query(Notes.CONTENT_DATA_URI,
                new String[] { CallNote.NOTE_ID },
                CallNote.CALL_DATE + "=? AND " + CallNote.MIME_TYPE + "=? AND PHONE_NUMBERS_EQUAL("
                        + CallNote.PHONE_NUMBER + ",?)",
                new String[] { String.valueOf(callDate), CallNote.CONTENT_ITEM_TYPE, phoneNumber },
                null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                try {
                    return cursor.getLong(0); // 获取笔记 ID
                } catch (IndexOutOfBoundsException e) {
                    Log.e(TAG, "Get call note ID fails: " + e.toString());
                }
            }
            cursor.close();
        }
        return 0; // 如果未找到匹配的笔记，返回 0
    }

    /**
     * 根据笔记 ID 获取笔记的摘要。
     *
     * @param resolver ContentResolver 对象。
     * @param noteId 笔记 ID。
     * @return 笔记摘要。
     * @throws IllegalArgumentException 如果笔记不存在。
     */
    public static String getSnippetById(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI,
                new String[] { NoteColumns.SNIPPET },
                NoteColumns.ID + "=?",
                new String[] { String.valueOf(noteId) },
                null);

        if (cursor != null) {
            String snippet = "";
            if (cursor.moveToFirst()) {
                snippet = cursor.getString(0); // 获取笔记摘要
            }
            cursor.close();
            return snippet;
        }
        throw new IllegalArgumentException("Note is not found with ID: " + noteId);
    }

    /**
     * 格式化笔记摘要，仅保留第一行内容。
     *
     * @param snippet 原始摘要。
     * @return 格式化后的摘要。
     */
    public static String getFormattedSnippet(String snippet) {
        if (snippet != null) {
            snippet = snippet.trim(); // 去除首尾空格
            int index = snippet.indexOf('\n'); // 查找换行符
            if (index != -1) {
                snippet = snippet.substring(0, index); // 截取第一行
            }
        }
        return snippet;
    }
}