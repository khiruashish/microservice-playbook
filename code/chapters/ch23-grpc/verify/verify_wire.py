"""Cross-checks the chapter's hand-written wire encoding against Google's protobuf runtime.

Builds tracking.proto's messages from descriptors at runtime (no protoc needed), encodes the same
OrderStatus, and compares bytes with the Java output; checks the canonical JSON mapping; and shows
that a reader built from the v1 schema keeps (and ignores) a field added in v2.
Usage: python3 verify_wire.py <hex from PayloadComparison>
"""
import json, sys
from google.protobuf import descriptor_pb2, descriptor_pool, json_format, message_factory, timestamp_pb2

def build(extra_field=False):
    pool = descriptor_pool.DescriptorPool()
    pool.AddSerializedFile(timestamp_pb2.DESCRIPTOR.serialized_pb)
    f = descriptor_pb2.FileDescriptorProto(name="tracking.proto", package="foodfleet.tracking.v1", syntax="proto3",
                                           dependency=["google/protobuf/timestamp.proto"])
    T = descriptor_pb2.FieldDescriptorProto
    stage = f.enum_type.add(name="Stage")
    for i, n in enumerate(["STAGE_UNSPECIFIED", "PLACED", "PREPARING", "PICKED_UP", "DELIVERED", "CANCELLED"]):
        stage.value.add(name=n, number=i)
    geo = f.message_type.add(name="GeoPoint")
    geo.field.add(name="latitude", number=1, type=T.TYPE_DOUBLE, label=T.LABEL_OPTIONAL)
    geo.field.add(name="longitude", number=2, type=T.TYPE_DOUBLE, label=T.LABEL_OPTIONAL)
    rider = f.message_type.add(name="Rider")
    rider.field.add(name="rider_id", number=1, type=T.TYPE_STRING, label=T.LABEL_OPTIONAL)
    rider.field.add(name="name", number=2, type=T.TYPE_STRING, label=T.LABEL_OPTIONAL)
    rider.field.add(name="location", number=3, type=T.TYPE_MESSAGE, type_name=".foodfleet.tracking.v1.GeoPoint", label=T.LABEL_OPTIONAL)
    st = f.message_type.add(name="OrderStatus")
    st.field.add(name="order_id", number=1, type=T.TYPE_STRING, label=T.LABEL_OPTIONAL)
    st.field.add(name="stage", number=2, type=T.TYPE_ENUM, type_name=".foodfleet.tracking.v1.Stage", label=T.LABEL_OPTIONAL)
    st.field.add(name="eta_minutes", number=3, type=T.TYPE_INT32, label=T.LABEL_OPTIONAL)
    st.field.add(name="rider", number=4, type=T.TYPE_MESSAGE, type_name=".foodfleet.tracking.v1.Rider", label=T.LABEL_OPTIONAL)
    st.field.add(name="updated_at", number=5, type=T.TYPE_MESSAGE, type_name=".google.protobuf.Timestamp", label=T.LABEL_OPTIONAL)
    st.field.add(name="restaurant_name", number=6, type=T.TYPE_STRING, label=T.LABEL_OPTIONAL)
    if extra_field:   # v2 adds a field with a NEW number (7 is reserved)
        st.field.add(name="promised_by", number=8, type=T.TYPE_MESSAGE, type_name=".google.protobuf.Timestamp", label=T.LABEL_OPTIONAL)
    pool.Add(f)
    return message_factory.GetMessageClass(pool.FindMessageTypeByName("foodfleet.tracking.v1.OrderStatus"))

OrderStatus = build()
m = OrderStatus()
m.order_id = "o-20261006-000042"; m.stage = 3; m.eta_minutes = 17
m.rider.rider_id = "r-7731"; m.rider.name = "Suresh K"
m.rider.location.latitude = 12.935192; m.rider.location.longitude = 77.624481
m.updated_at.FromJsonString("2026-10-06T13:12:45.120Z")
m.restaurant_name = "Meghana Foods, Koramangala"

official = m.SerializeToString()
java = bytes.fromhex(sys.argv[1])
print("official protobuf runtime:", len(official), "bytes")
print("Java ProtoWriter:         ", len(java), "bytes")
print("byte-for-byte identical:  ", official == java)
parsed = OrderStatus.FromString(java)
print("official parser reads the Java bytes back equal:", parsed == m)
print("canonical proto3 JSON:", json.dumps(json.loads(json_format.MessageToJson(m)), separators=(",", ":")))

V2 = build(extra_field=True)
v2 = V2.FromString(official)
v2.promised_by.FromJsonString("2026-10-06T13:30:00Z")
v2_bytes = v2.SerializeToString()
old = OrderStatus.FromString(v2_bytes)                       # a v1 client reads a v2 message
print("v1 reader on v2 bytes: fields it knows intact:", old.order_id == m.order_id and old.eta_minutes == 17,
      "| unknown field kept, re-serialised:", old.SerializeToString() == v2_bytes)
