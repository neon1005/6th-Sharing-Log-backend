package gdg.sharinglog.service.group.result;

public record UpdatedGroup(
        String groupPublicId,
        String name,
        String address
) {
}
