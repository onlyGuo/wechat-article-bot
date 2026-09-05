package ink.icoding.wechat.article.asset;

import ink.icoding.smartmybatis.entity.expression.Where;
import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AssetMapper extends SmartMapper<Asset> {
    default Asset findById(Long id) {
        return selectById(id);
    }

    default List<Asset> findAll(Long accountId) {
        return select(Where.where().ifAnd(Asset::getAccountId).eq(accountId)
                .orderBy(Asset::getCreatedAt).desc().limit(200));
    }

    default Asset findByStorageName(String storageName) {
        return selectOne(Where.where(Asset::getStorageName).eq(storageName));
    }

    default int updateWechatMedia(Long id, Long accountId, String wechatMediaId) {
        Asset asset = selectById(id);
        if (asset == null) return 0;
        asset.setAccountId(accountId);
        asset.setWechatMediaId(wechatMediaId);
        return updateById(asset);
    }

    default int updateStoragePath(Long id, String storagePath) {
        Asset asset = selectById(id);
        if (asset == null) return 0;
        asset.setStoragePath(storagePath);
        return updateById(asset);
    }

    default int updateWechatContentUrl(Long id, String wechatContentUrl) {
        Asset asset = selectById(id);
        if (asset == null) return 0;
        asset.setWechatContentUrl(wechatContentUrl);
        return updateById(asset);
    }
}
