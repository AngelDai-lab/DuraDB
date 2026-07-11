package com.duradb.storage;

/* 记录 ID
 * 定位一条记录的唯一标识：它在第几页的第几个槽*/
public class RecordId {
    
    private final int pageId;      // 页号
    private final int slotIndex;   // 槽索引
    
    public RecordId(int pageId, int slotIndex) {
        this.pageId = pageId;
        this.slotIndex = slotIndex;
    }
    
    public int getPageId() {
        return pageId;
    }
    
    public int getSlotIndex() {
        return slotIndex;
    }
    
    @Override
    public String toString() {
        return "RecordId{page=" + pageId + ", slot=" + slotIndex + "}";
    }
}