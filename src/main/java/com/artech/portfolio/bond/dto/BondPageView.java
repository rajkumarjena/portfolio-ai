package com.artech.portfolio.bond.dto;

import lombok.Data;

import java.util.List;

/**
 * BONDBP_V9 #9/#11: one server-side page of the Bond Records screen. The
 * active table holds a few hundred records and the matured section keeps
 * growing, so slicing, ordering and the section split all happen on the
 * server - the screen just renders the current page.
 */
@Data
public class BondPageView {

    private List<BondRowView> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static BondPageView of(List<BondRowView> content, int page, int size, long totalElements) {
        BondPageView view = new BondPageView();
        view.setContent(content);
        view.setPage(page);
        view.setSize(size);
        view.setTotalElements(totalElements);
        view.setTotalPages(size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size));
        return view;
    }
}
