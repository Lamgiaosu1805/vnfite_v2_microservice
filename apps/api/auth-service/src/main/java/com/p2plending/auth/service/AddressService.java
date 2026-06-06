package com.p2plending.auth.service;

import com.p2plending.auth.domain.repository.WardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final WardRepository wardRepository;

    /**
     * Trả về danh sách tên xã/phường/thị trấn theo tỉnh/thành.
     * Kết quả được cache trên Redis theo namespace môi trường — dữ liệu tĩnh, không cần invalidate thường xuyên.
     */
    @Cacheable(value = "wards", key = "#provinceCode")
    @Transactional(readOnly = true)
    public List<String> getWardsByProvince(String provinceCode) {
        return wardRepository
                .findByProvinceCodeAndIsDeletedFalseOrderByNameAsc(provinceCode)
                .stream()
                .map(w -> w.getName())
                .toList();
    }
}
