package com.company.depcheck.model.vo;

import java.util.List;

/**
 * 分页结果（结果页表格用）。
 *
 * @param <T> 记录类型
 * @author depcheck
 * @since 1.0.0
 */
public class PageResult<T> {

    /** 总条数。 */
    private long total;

    /** 当前页（从 1 开始）。 */
    private long page;

    /** 每页条数。 */
    private long size;

    /** 当前页数据。 */
    private List<T> records;

    public PageResult() {
    }

    /**
     * 构造分页结果。
     *
     * @param total   总条数
     * @param page    当前页
     * @param size    每页条数
     * @param records 当前页数据
     */
    public PageResult(long total, long page, long size, List<T> records) {
        this.total = total;
        this.page = page;
        this.size = size;
        this.records = records;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }
}
