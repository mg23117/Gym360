package com.example.gym360.data.mapper;

import com.example.gym360.data.local.entity.MembershipEntity;
import com.example.gym360.data.model.MembershipDto;
import com.example.gym360.domain.model.Membership;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MembershipMapper {

    public static Membership fromEntityToDomain(MembershipEntity entity) {
        if (entity == null) return null;
        return new Membership(
                entity.getId(),
                entity.getName(),
                entity.getDurationMonths(),
                entity.getPrice(),
                new ArrayList<>(entity.getBenefits())
        );
    }

    public static MembershipEntity fromDomainToEntity(Membership domain, String status) {
        if (domain == null) return null;
        List<String> benefits = domain.getBenefits() == null
                ? Collections.emptyList() : domain.getBenefits();
        return new MembershipEntity(domain.getId(), domain.getName(), domain.getDurationMonths(),
                domain.getPrice(), benefits, status, 1);
    }

    public static MembershipDto toDto(MembershipEntity entity) {
        MembershipDto dto = new MembershipDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDurationMonths(entity.getDurationMonths());
        dto.setPrice(entity.getPrice());
        dto.setBenefits(new ArrayList<>(entity.getBenefits()));
        return dto;
    }

    public static MembershipEntity fromDto(MembershipDto dto) {
        if (dto.getId() == null || dto.getId().trim().isEmpty() ||
                dto.getName() == null || dto.getName().trim().isEmpty() ||
                dto.getDurationMonths() <= 0 || !Double.isFinite(dto.getPrice()) ||
                dto.getPrice() < 0) {
            throw new IllegalArgumentException("Plan inválido recibido de Firestore");
        }
        return new MembershipEntity(dto.getId(), dto.getName(), dto.getDurationMonths(),
                dto.getPrice(), dto.getBenefits() == null ? Collections.emptyList() : dto.getBenefits(),
                MembershipEntity.SYNCED, 0);
    }

}
